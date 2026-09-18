package com.november.mcphone.core.script.server.economy;

import com.november.mcphone.api.economy.TxnReason;
import com.november.mcphone.api.economy.TxnResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 流水（施工方案 §22.10）。<b>一天一个文件，追加写。</b>
 *
 * <pre>world/mcphone/economy/ledger/&lt;yyyy-MM-dd&gt;.log</pre>
 *
 * <p>字段顺序照 §22.10 的原文：
 * {@code 时间 | 货币id | 类型 | from | to | 金额 | appId | kind | ref | 结果}。
 * 以 {@code #} 开头的是注释行（存档点、重启说明），不满 10 格。
 *
 * <h2>这是经济系统唯一的安全网</h2>
 *
 * 没有它，通胀发生了也没人知道从哪来（§22.10）。所以<b>每一笔变动都要进</b>（§22.3 ⑥），
 * 包括失败的那些 —— 失败的尝试正是查"谁在试探"的依据。
 *
 * <h2>appId 由宿主盖章，不采信调用方</h2>
 *
 * {@link TxnReason} 上<b>没有</b> appId 这一格（S11 定的）：让被监督的一方填写自己是谁，
 * 安全网就不成其为网。appId 由调用上下文传进来，写这一行时拼上。
 */
public final class TxnLog {

    /** 保留多少天（§22.10）。 */
    public static final int RETENTION_DAYS = 90;

    /** 单日文件上限，超出轮转（§22.10）。 */
    public static final long MAX_FILE_BYTES = 64L * 1024 * 1024;

    /** 分隔符。{@link TxnReason} 的两个字段在构造时就拒了它与换行，所以这一行不会被伪造。 */
    private static final char SEP = '|';

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path dir;
    private final ZoneId zone;
    private final Journal journal;

    public TxnLog(Path economyDir, ZoneId zone) {
        this(economyDir, zone, null);
    }

    public TxnLog(Path economyDir, ZoneId zone, Journal journal) {
        this.dir = economyDir.resolve("ledger");
        this.zone = zone;
        this.journal = journal;
    }

    /**
     * 每记一行都告诉它（实现在 {@link EconomyData}，与余额同一次落盘）：
     * <ul>
     *   <li>成功的 mint / burn 记进累计 —— 对账读累计，不读流水文件：流水 {@link #RETENTION_DAYS} 天后会被清掉，
     *       强杀之后还会比存档超前一截，拿它对账要么早晚不平、要么重启之后不平。</li>
     *   <li>任何一笔成功都标脏 —— 下一次世界保存必定写存档点（{@link #checkpoint}）。计分板档的转账不碰这份存档，
     *       不标脏的话它们永远排在最后一个存档点之后，正常停服再开也会被当成"没进存档"。</li>
     * </ul>
     */
    public interface Journal {
        void recorded(String currencyId, Kind kind, long amount, TxnResult result);
    }

    /** 一笔变动的种类。 */
    public enum Kind {
        TRANSFER, MINT, BURN, HOLD, RELEASE, REFUND
    }

    /** 记一行。成功的 mint / burn 同时记进 {@link Journal} 的累计 —— 与余额的改动在同一个主线程操作里。 */
    public void append(Instant at, String currencyId, Kind kind, UUID from, UUID to,
                       long amount, String appId, TxnReason reason, TxnResult result) {
        if (journal != null) journal.recorded(currencyId, kind, amount, result);
        String line = String.join(String.valueOf(SEP),
                at.toString(),
                currencyId,
                kind.name().toLowerCase(java.util.Locale.ROOT),
                from == null ? "-" : from.toString(),
                to == null ? "-" : to.toString(),
                Long.toString(amount),
                appId == null ? "-" : appId,
                reason == null ? "-" : reason.kind(),
                reason == null ? "-" : reason.ref(),
                result.name());
        write(at, line);
    }

    /** 注释行的开头。{@link #sumMintAndBurn} 这类按竖线切的解析，切出来不满 10 格，自然跳过。 */
    private static final String CHECKPOINT = "# 存档点 ";

    /**
     * 世界保存时写一行存档点（{@link EconomyData} 在序列化时调）。开服时拿它判断流水比存档超前了几笔。
     *
     * <p>序列化与真正写盘之间被强杀的话，这一行会多说一次"存过了"，那一个保存周期里的变动就漏报了。
     */
    public void checkpoint(Instant at) {
        write(at, CHECKPOINT + at);
    }

    /**
     * 开服时调：上一个存档点之后还有成功的变动，说明它们没进存档（强杀或崩溃），写一行标出来。
     * 不标出来，服主会拿着一行「A 付给 B 100」去对一笔并没有生效的账。
     *
     * <p>最后<b>要补一个存档点</b>：开服这一刻盘上的存档就是现状。不补的话，重启后一直没有成功变动时存档不脏、
     * 不会写存档点，下次开服又把同一批行报一遍。两种情况不补：还没有流水目录（从没用过货币的世界，别为它建目录），
     * 以及流水读不出来（补了就再也报不出那批没进存档的行）。
     *
     * <p>只看最新的两个文件：跨两个文件还找不到存档点就是老流水、判断不了，不报。
     *
     * @return 没进存档的成功变动有几笔；流水读不出来是 -1
     */
    public int noteRestart(Instant now) {
        if (!Files.isDirectory(dir)) return 0;
        int unsaved = unsavedSinceCheckpoint();
        if (unsaved < 0) return -1;
        if (unsaved > 0) {
            write(now, "# " + now + " 重启：上一个存档点之后有 " + unsaved
                    + " 笔成功的变动没进存档（强杀或崩溃），以存档为准");
        }
        checkpoint(now);
        return unsaved;
    }

    /** 最后一个存档点之后的成功变动有几笔；找不到存档点是 0，读不出来是 -1。 */
    private int unsavedSinceCheckpoint() {
        List<Path> files = new ArrayList<>();
        try (var s = Files.list(dir)) {
            for (Path p : s.toList()) {
                if (p.getFileName().toString().endsWith(".log")) files.add(p);
            }
            files.sort((x, y) -> {
                try {
                    return Files.getLastModifiedTime(y).compareTo(Files.getLastModifiedTime(x));
                } catch (IOException e) {
                    return 0;
                }
            });
        } catch (IOException e) {
            com.november.mcphone.MCphone.LOGGER.warn("[MCphone] 读流水失败: {}", e.toString());
            return -1;
        }
        int unsaved = 0;
        for (Path p : files.subList(0, Math.min(2, files.size()))) {
            List<String> lines;
            try {
                lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            } catch (IOException e) {
                com.november.mcphone.MCphone.LOGGER.warn("[MCphone] 读流水失败: {}", e.toString());
                return -1;
            }
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i);
                if (line.startsWith(CHECKPOINT)) return unsaved;
                String[] f = line.split("\\|", -1);
                if (f.length >= 10 && "OK".equals(f[9])) unsaved++;
            }
        }
        return 0;
    }

    private void write(Instant at, String line) {
        try {
            Files.createDirectories(dir);
            Path f = fileFor(at);
            if (Files.exists(f) && Files.size(f) >= MAX_FILE_BYTES) f = rotated(f);
            Files.writeString(f, encodable(line) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 写不了流水是要命的（对账就断了），但不能因此把这笔交易也搞砸 —— 交易已经做完了
            com.november.mcphone.MCphone.LOGGER.error("[MCphone] 流水写不进去，对账会断: {}", e.toString());
        }
    }

    /**
     * 孤立的代理字符换成 U+FFFD。{@code reason.ref} 是脚本给的，里面一个孤立的 0xD800 就能让 UTF-8 编码抛异常，
     * 而写失败只记日志 —— 一笔成功的转账就这样不进流水了。
     */
    static String encodable(String s) {
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean lone = Character.isHighSurrogate(c)
                    ? i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))
                    : Character.isLowSurrogate(c) && (i == 0 || !Character.isHighSurrogate(s.charAt(i - 1)));
            if (lone) {
                if (b == null) b = new StringBuilder(s.substring(0, i));
                b.append('\uFFFD');
            } else {
                if (b != null) b.append(c);
            }
        }
        return b == null ? s : b.toString();
    }

    Path fileFor(Instant at) {
        return dir.resolve(DAY.format(at.atZone(zone)) + ".log");
    }

    /** 同一天写满 64 MiB 之后换一个带序号的。 */
    private Path rotated(Path base) throws IOException {
        String name = base.getFileName().toString().replace(".log", "");
        for (int i = 1; i < 1000; i++) {
            Path p = dir.resolve(name + "." + i + ".log");
            if (!Files.exists(p) || Files.size(p) < MAX_FILE_BYTES) return p;
        }
        return base;
    }

    /** 扫掉过保留期的（§22.10）。返回删了几个。 */
    public int sweep(Instant now) {
        if (!Files.isDirectory(dir)) return 0;
        long cutoff = now.minusSeconds(RETENTION_DAYS * 86400L).toEpochMilli();
        int n = 0;
        try (var s = Files.list(dir)) {
            List<Path> old = new ArrayList<>();
            for (Path p : s.toList()) {
                if (Files.getLastModifiedTime(p).toMillis() < cutoff) old.add(p);
            }
            for (Path p : old) {
                Files.deleteIfExists(p);
                n++;
            }
        } catch (IOException e) {
            com.november.mcphone.MCphone.LOGGER.warn("[MCphone] 清流水失败: {}", e.toString());
        }
        return n;
    }

    /** 把所有流水里的 mint 与 burn 加起来，给对账用。 */
    public long[] sumMintAndBurn(String currencyId) {
        long mint = 0, burn = 0;
        if (!Files.isDirectory(dir)) return new long[]{0, 0};
        try (var s = Files.list(dir)) {
            for (Path p : s.toList()) {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    String[] f = line.split("\\|", -1);
                    if (f.length < 10 || !currencyId.equals(f[1])) continue;
                    if (!"OK".equals(f[9])) continue;              // 失败的不算进总量
                    long amt;
                    try {
                        amt = Long.parseLong(f[5]);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    if ("mint".equals(f[2])) mint += amt;
                    else if ("burn".equals(f[2])) burn += amt;
                }
            }
        } catch (IOException e) {
            com.november.mcphone.MCphone.LOGGER.warn("[MCphone] 读流水失败: {}", e.toString());
        }
        return new long[]{mint, burn};
    }
}
