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
 * {@code 时间 | 货币id | 类型 | from | to | 金额 | appId | kind | ref | 结果}
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

    public TxnLog(Path economyDir, ZoneId zone) {
        this.dir = economyDir.resolve("ledger");
        this.zone = zone;
    }

    /** 一笔变动的种类。 */
    public enum Kind {
        TRANSFER, MINT, BURN, HOLD, RELEASE, REFUND
    }

    /** 记一行。 */
    public void append(Instant at, String currencyId, Kind kind, UUID from, UUID to,
                       long amount, String appId, TxnReason reason, TxnResult result) {
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

    private void write(Instant at, String line) {
        try {
            Files.createDirectories(dir);
            Path f = fileFor(at);
            if (Files.exists(f) && Files.size(f) >= MAX_FILE_BYTES) f = rotated(f);
            Files.writeString(f, line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 写不了流水是要命的（对账就断了），但不能因此把这笔交易也搞砸 —— 交易已经做完了
            com.november.mcphone.MCphone.LOGGER.error("[MCphone] 流水写不进去，对账会断: {}", e.toString());
        }
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
