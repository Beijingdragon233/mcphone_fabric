package com.november.mcphone.core.script.server.economy;

import com.november.mcphone.MCphone;
import com.november.mcphone.api.economy.TxnResult;
import com.november.mcphone.core.PhoneSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

/**
 * 货币的权威状态（施工方案 §22.4、§22.10）：每种货币的余额（UUID → 最小单位）、铸造与销毁的累计、
 * 托管账本。世界级存档，挂主世界 —— 余额挂在玩家身上的话，离线玩家就收不到钱（§22.4）。
 *
 * <p><b>只许服务端主线程碰，没有锁</b>（{@link CurrencyGateway}）。改动与 {@code setDirty()} 在同一个主线程操作里；
 * 什么时候写盘归 MC 的世界保存，我们不自己写文件 —— 所以强杀之后看到的是上一次保存时那一份完整的快照，
 * <b>前提是那一次写盘本身没被打断</b>：NeoForge 写临时文件再原子改名；Forge 1.20.1 与 Fabric（原版）就地截断重写，
 * 写到一半被强杀，文件就坏了 —— 那时下面的锁保证不拿空账盖掉它，但上一份快照要从服务器备份里恢复。
 *
 * <p><b>读坏了绝不拿一本空账顶上</b>：原版读档出错会新建一份空的，下次保存就把原文件盖掉，等于清空所有人的钱。
 * <ul>
 *   <li>整份读不出来（文件在但没读进来、版本认不得）→ 整个锁住：凡是要记进这份存档的操作一律 {@code UNAVAILABLE}，
 *       {@link #isDirty()} 恒为 false，原文件一个字节都不动。</li>
 *   <li>某一种货币读坏了 → 只锁那一种，它的原始数据原样写回。</li>
 * </ul>
 * 字段缺失取默认、多出来的字段忽略；类型不对才算读坏。<b>例外是缺了会动到钱的字段</b>（托管的 settled、amount）：
 * settled 缺了按"没结清"读，放过款的那笔就能再放一次 —— 这类缺失同样算读坏。
 */
public final class EconomyData extends PhoneSavedData implements BalanceStore, TxnLog.Journal {

    static final String FILE_NAME = MCphone.MODID + "_economy";

    /** 存档格式的版本。改格式就 +1，并在 {@link #MIGRATIONS} 里补一步；认不得的版本一律锁住，不猜。 */
    public static final int DATA_VERSION = 1;

    /** 从第 n 版升到第 n+1 版的那一步。目前只有第 1 版，所以是空的。 */
    private static final Map<Integer, UnaryOperator<CompoundTag>> MIGRATIONS = Map.of();

    /** 锁住时给调用方的原因（翻译键）。细节在日志里。 */
    public static final String KEY_LOCKED = "mcphone.economy.unavailable.data_locked";

    private final Map<String, Map<UUID, Long>> balances = new HashMap<>();
    /** 货币 id → {铸造累计, 销毁累计} */
    private final Map<String, long[]> supply = new HashMap<>();
    /** 读坏了的货币 → 它在存档里的原样，存档时原样写回；null = 存档里本来就没有这一段 */
    private final Map<String, Tag> lockedCurrencies = new LinkedHashMap<>();
    /** 属于锁住的货币的托管条目，原样写回 */
    private final List<Tag> lockedEscrow = new ArrayList<>();
    /** 整份锁住的原因；null = 没锁 */
    private final String wholeLock;
    private final EscrowLedger escrow;
    /** 世界保存、序列化这一份时调：给流水写存档点（{@link TxnLog#checkpoint}） */
    private Runnable onSave = () -> { };

    private EconomyData(LongSupplier clock, String wholeLock) {
        this.wholeLock = wholeLock;
        this.escrow = new EscrowLedger(clock, EscrowLedger.DEFAULT_TIMEOUT_MS, this::setDirty, this::unavailableReasonKey);
    }

    /** 新世界、或者测试用的一本空账。 */
    public static EconomyData empty(LongSupplier clock) {
        return new EconomyData(clock, null);
    }

    /** 整份锁住的一本。原因要醒目地进日志：服主不看日志就只会看到「货币用不了」。 */
    static EconomyData locked(String reason, LongSupplier clock) {
        MCphone.LOGGER.error("[MCphone] ⚠⚠ 货币存档锁住了，凡是要记进这份存档的货币操作都会返回 UNAVAILABLE，存档文件不会被改写 ⚠⚠ 原因：{}", reason);
        return new EconomyData(clock, reason);
    }

    /** 必须挂在主世界的 DataStorage：它按维度分，挂错了玩家去下界钱就「没了」且不报错。 */
    public static EconomyData get(MinecraftServer server) {
        LongSupplier clock = System::currentTimeMillis;
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME + ".dat");
        return getOrCreate(server, FILE_NAME, () -> createFor(file, clock), tag -> load(tag, clock));
    }

    /**
     * 原版要一本新的时候给什么。原版读档抛异常时不报给我们，而是回头来这里要一本新的 ——
     * 所以"文件在却要新建"就是读坏了，给一本整份锁住的，绝不给空账。
     */
    static EconomyData createFor(Path file, LongSupplier clock) {
        return Files.exists(file)
                ? locked("存档文件 " + file + " 在，但没读出来（见上面原版的报错）", clock)
                : empty(clock);
    }

    // ---------------------------------------------------------------- 状态

    /** 整份锁住的原因；没锁就是 null。 */
    public String wholeLock() {
        return wholeLock;
    }

    /** 锁住的那几种货币。 */
    public Set<String> lockedCurrencies() {
        return Set.copyOf(lockedCurrencies.keySet());
    }

    public EscrowLedger escrow() {
        return escrow;
    }

    /** 账上出现过的所有货币，对账用：有余额的、有累计的、有托管的、锁住的。 */
    public Set<String> currencyIds() {
        Set<String> out = new TreeSet<>(balances.keySet());
        out.addAll(supply.keySet());
        out.addAll(lockedCurrencies.keySet());
        for (EscrowLedger.Entry e : escrow.snapshot().values()) out.add(e.currencyId());
        return out;
    }

    /** {铸造累计, 销毁累计}，对账用。 */
    public long[] supply(String currencyId) {
        long[] s = supply.get(currencyId);
        return s == null ? new long[]{0, 0} : s.clone();
    }

    /** 开服时接上流水的存档点。 */
    public void onSave(Runnable hook) {
        this.onSave = hook;
    }

    /** 整份锁住时永远不脏：MC 就不会拿这一份去盖原文件。 */
    @Override
    public boolean isDirty() {
        return wholeLock == null && super.isDirty();
    }

    // ---------------------------------------------------------------- BalanceStore

    @Override
    public String unavailableReasonKey(String currencyId) {
        return wholeLock != null || lockedCurrencies.containsKey(currencyId) ? KEY_LOCKED : null;
    }

    @Override
    public long get(UUID player, String currencyId) {
        requireUnlocked(currencyId);
        Map<UUID, Long> m = balances.get(currencyId);
        return m == null ? 0 : m.getOrDefault(player, 0L);
    }

    /** 余额为 0 的条目不留：离线玩家收过一次钱又花光，不该在存档里占一行一辈子。 */
    @Override
    public void set(UUID player, String currencyId, long value) {
        requireUnlocked(currencyId);
        Map<UUID, Long> m = balances.computeIfAbsent(currencyId, k -> new HashMap<>());
        if (value == 0) m.remove(player);
        else m.put(player, value);
        if (m.isEmpty()) balances.remove(currencyId);
        setDirty();
    }

    @Override
    public Map<UUID, Long> all(String currencyId) {
        requireUnlocked(currencyId);
        Map<UUID, Long> m = balances.get(currencyId);
        return m == null ? Map.of() : Map.copyOf(m);
    }

    // provider 在动账之前就判过锁；走到这里说明有人绕过了它，宁可炸也不许往锁住的账上写
    private void requireUnlocked(String currencyId) {
        if (unavailableReasonKey(currencyId) != null) {
            throw new IllegalStateException("货币 " + currencyId + " 的存档锁住了，不许读写");
        }
    }

    // ---------------------------------------------------------------- TxnLog.Journal

    @Override
    public void recorded(String currencyId, TxnLog.Kind kind, long amount, TxnResult result) {
        if (result != TxnResult.OK) return;
        setDirty();
        if (kind != TxnLog.Kind.MINT && kind != TxnLog.Kind.BURN) return;
        long[] s = supply.computeIfAbsent(currencyId, k -> new long[2]);
        int i = kind == TxnLog.Kind.MINT ? 0 : 1;
        // 累计撑破 long 时封顶而不是抛：钱已经动了，这里再抛只会让余额与累计一个改了一个没改
        long next = s[i] + amount;
        if (((s[i] ^ next) & (amount ^ next)) < 0) {
            MCphone.LOGGER.error("[MCphone] 货币 {} 的{}累计撑破了 long，封顶；对账会显示不平", currencyId,
                    i == 0 ? "铸造" : "销毁");
            next = Long.MAX_VALUE;
        }
        s[i] = next;
    }

    // ---------------------------------------------------------------- 存档

    @Override
    protected CompoundTag write(CompoundTag tag) {
        tag.putInt("dataVersion", DATA_VERSION);

        CompoundTag currencies = new CompoundTag();
        Set<String> ids = new TreeSet<>(balances.keySet());
        ids.addAll(supply.keySet());
        for (String id : ids) {
            CompoundTag c = new CompoundTag();
            CompoundTag bal = new CompoundTag();
            balances.getOrDefault(id, Map.of()).forEach((p, v) -> bal.putLong(p.toString(), v));
            c.put("balances", bal);
            long[] s = supply(id);
            c.putLong("minted", s[0]);
            c.putLong("burned", s[1]);
            currencies.put(id, c);
        }
        lockedCurrencies.forEach((id, raw) -> {
            if (raw != null) currencies.put(id, raw.copy());
        });
        tag.put("currencies", currencies);

        ListTag list = new ListTag();
        escrow.snapshot().forEach((id, e) -> {
            CompoundTag t = new CompoundTag();
            t.putString("id", id.toString());
            t.putString("owner", e.owner().toString());
            t.putString("beneficiary", e.beneficiary().toString());
            t.putString("currency", e.currencyId());
            t.putLong("amount", e.amount());
            t.putLong("createdAt", e.createdAt());
            t.putBoolean("settled", e.settled());
            if (e.settled()) t.putLong("settledAt", e.settledAt());
            list.add(t);
        });
        for (Tag raw : lockedEscrow) list.add(raw.copy());
        tag.put("escrow", list);
        onSave.run();
        return tag;
    }

    /** 给测试用：不经过 MC 的存档机制，拿到这一份写出去会是什么样。 */
    public CompoundTag toTag() {
        return write(new CompoundTag());
    }

    /**
     * 从存档读回来。<b>不抛</b>：读不懂的部分按上面的规则锁住，原样留着写回去。
     * （抛了也兜得住 —— 原版会转头调 {@link #get} 里那个 create，而文件在，于是整份锁住 —— 但那样就丢了"只锁一种"的精度。）
     */
    public static EconomyData load(CompoundTag tag, LongSupplier clock) {
        if (!tag.contains("dataVersion", Tag.TAG_INT)) {
            return locked("存档里没有 dataVersion（这一版起每份都写），认不出是什么格式，不猜", clock);
        }
        int v = tag.getInt("dataVersion");
        if (v > DATA_VERSION) {
            return locked("存档是更新版本的 MCphone 写的（格式第 " + v + " 版，这一版只认到第 " + DATA_VERSION
                    + " 版）；不降级、不覆盖，装回新版本就好", clock);
        }
        CompoundTag t = tag;
        for (int from = v; from < DATA_VERSION; from++) {
            UnaryOperator<CompoundTag> step = MIGRATIONS.get(from);
            if (step == null) return locked("存档格式第 " + from + " 版没有迁移到第 " + (from + 1) + " 版的办法", clock);
            try {
                t = step.apply(t.copy());
            } catch (RuntimeException e) {
                return locked("存档格式从第 " + from + " 版迁移失败：" + e, clock);
            }
        }

        EconomyData d = new EconomyData(clock, null);

        Map<String, Tag> rawCurrencies = new LinkedHashMap<>();
        if (t.contains("currencies")) {
            if (!t.contains("currencies", Tag.TAG_COMPOUND)) return locked("currencies 不是一张表", clock);
            CompoundTag cs = t.getCompound("currencies");
            for (String id : cs.getAllKeys()) {
                Tag raw = cs.get(id);
                rawCurrencies.put(id, raw);
                String why = canonicalCurrency(id) ? d.readCurrency(id, raw) : "货币 id 不是规范写法";
                if (why != null) d.lockCurrency(id, raw, why);
            }
        }

        if (t.contains("escrow")) {
            if (!t.contains("escrow", Tag.TAG_LIST)) return locked("escrow 不是一个列表", clock);
            ListTag list = t.getList("escrow", Tag.TAG_COMPOUND);
            if (list.size() != ((ListTag) t.get("escrow")).size()) {
                return locked("escrow 里有不是表的条目", clock);
            }
            Map<UUID, EscrowLedger.Entry> ok = new LinkedHashMap<>();
            List<CompoundTag> pending = new ArrayList<>();
            int[] fixed = {0};
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                if (!e.contains("currency", Tag.TAG_STRING)) {
                    return locked("第 " + (i + 1) + " 笔托管读不出是哪种货币，不知道该锁哪一种", clock);
                }
                String currency = e.getString("currency");
                // 大写之类的 id 不锁的话，这笔就成了哪种货币都不认领的孤儿
                String why = canonicalCurrency(currency) ? readEscrow(e, ok, clock.getAsLong(), fixed) : "的货币 id 不是规范写法";
                if (why != null) d.lockCurrency(currency, rawCurrencies.get(currency), "第 " + (i + 1) + " 笔托管" + why);
                pending.add(e);
            }
            // 锁住的货币（不管是余额读坏还是托管读坏）它的托管条目一条都不放进账本，原样留着写回
            Map<UUID, EscrowLedger.Entry> live = new LinkedHashMap<>();
            for (CompoundTag e : pending) {
                String currency = e.getString("currency");
                if (d.lockedCurrencies.containsKey(currency)) {
                    d.lockedEscrow.add(e);
                } else {
                    UUID id = UUID.fromString(e.getString("id"));
                    live.put(id, ok.get(id));
                }
            }
            d.escrow.restore(live);
            // 改正过的建立时刻要落盘：不落的话每次开服都按那一刻重新计时，这笔永远等不到期
            if (fixed[0] > 0) d.setDirty();
        }
        return d;
    }

    /** 读一种货币。读不懂返回原因，读懂了返回 null。 */
    private String readCurrency(String id, Tag raw) {
        if (!(raw instanceof CompoundTag c)) return "不是一张表";
        Map<UUID, Long> m = new HashMap<>();
        if (c.contains("balances")) {
            if (!c.contains("balances", Tag.TAG_COMPOUND)) return "balances 不是一张表";
            CompoundTag bal = c.getCompound("balances");
            for (String k : bal.getAllKeys()) {
                UUID p = canonicalUuid(k);
                // 非规范写法（大写、省了前导零）会被 fromString 归一：两个键落到同一个人，后一个静默盖掉前一个
                if (p == null) return "balances 里 " + k + " 不是规范写法的 UUID";
                if (!bal.contains(k, Tag.TAG_LONG)) return "balances 里 " + k + " 的余额不是 long";
                long value = bal.getLong(k);
                if (value != 0) m.put(p, value);
            }
        }
        long[] s = new long[2];
        String[] keys = {"minted", "burned"};
        for (int i = 0; i < 2; i++) {
            if (!c.contains(keys[i])) continue;
            if (!c.contains(keys[i], Tag.TAG_LONG)) return keys[i] + " 不是 long";
            s[i] = c.getLong(keys[i]);
            if (s[i] < 0) return keys[i] + " 是负数";
        }
        if (!m.isEmpty()) balances.put(id, m);
        if (s[0] != 0 || s[1] != 0) supply.put(id, s);
        return null;
    }

    /** 读一笔托管。读不懂返回原因。 */
    private static String readEscrow(CompoundTag e, Map<UUID, EscrowLedger.Entry> out, long now, int[] fixed) {
        UUID id = canonicalUuid(e.getString("id"));
        UUID owner = canonicalUuid(e.getString("owner"));
        UUID beneficiary = canonicalUuid(e.getString("beneficiary"));
        if (id == null || owner == null || beneficiary == null) return "的 id / owner / beneficiary 不是规范写法的 UUID";
        if (!e.contains("amount", Tag.TAG_LONG) || !e.contains("createdAt", Tag.TAG_LONG)) {
            return "缺 amount 或 createdAt，或者不是 long";
        }
        if (e.getLong("amount") <= 0) return "的金额不是正数";
        long createdAt = e.getLong("createdAt");
        // settled 缺了不取默认：按"没结清"读，放过款的那笔就能再放一次
        if (!e.contains("settled", Tag.TAG_BYTE)) return "缺 settled，或者不是布尔";
        if (out.containsKey(id)) return "的号 " + id + " 重复了";
        boolean settled = e.getBoolean("settled");
        // 超时判断是 now - createdAt ≥ 超时：建立时刻在将来（建托管时服务器时钟快了）或不是正数（负得离谱会溢出），
        // 这笔就永远不会被退款。没结清的改成此刻、从现在起再等满一个超时周期；不锁 —— 为一笔时间戳停掉整种货币代价太大。
        // 已结清的不会再到期，不管它
        if (!settled && (createdAt <= 0 || createdAt > now)) {
            MCphone.LOGGER.warn("[MCphone] 托管 {} 的建立时刻 {} 不对（在将来或不是正数），按此刻 {} 重新计时", id, createdAt, now);
            createdAt = now;
            fixed[0]++;
        }
        // settledAt 只决定已结清的条目留多久，缺了按建立时刻算，不动钱；但写了就得是 long
        if (e.contains("settledAt") && !e.contains("settledAt", Tag.TAG_LONG)) return "的 settledAt 不是 long";
        long settledAt = !settled ? 0 : e.contains("settledAt") ? e.getLong("settledAt") : e.getLong("createdAt");
        out.put(id, new EscrowLedger.Entry(owner, beneficiary, e.getString("currency"),
                e.getLong("amount"), createdAt, settled, settledAt));
        return null;
    }

    /** 货币 id 要是规范写法的 ResourceLocation（带命名空间、全小写），与 {@code CurrencySpec} 读进来的一致。 */
    private static boolean canonicalCurrency(String id) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        return rl != null && rl.toString().equals(id);
    }

    /** 只认 {@link UUID#toString()} 的规范写法；别的写法（大写、省了前导零）返回 null。 */
    private static UUID canonicalUuid(String s) {
        try {
            UUID u = UUID.fromString(s);
            return u.toString().equals(s) ? u : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 把一种货币锁住：已经读进来的余额与累计撤掉，原样留着写回。 */
    private void lockCurrency(String id, Tag raw, String why) {
        if (lockedCurrencies.containsKey(id)) return;
        MCphone.LOGGER.error("[MCphone] ⚠⚠ 货币 {} 的存档读坏了（{}），这种货币锁住：操作一律 UNAVAILABLE，原始数据原样写回 ⚠⚠",
                id, why);
        balances.remove(id);
        supply.remove(id);
        // 只有托管、没有余额段的货币也可能被锁：那时原样就是"没有这一段"，写回时也不写
        lockedCurrencies.put(id, raw == null ? null : raw.copy());
    }
}
