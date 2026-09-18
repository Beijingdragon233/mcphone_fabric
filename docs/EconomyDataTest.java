package com.november.mcphone.core.script.server.economy;

import com.november.mcphone.api.economy.Currency;
import com.november.mcphone.api.economy.EscrowId;
import com.november.mcphone.api.economy.HoldResult;
import com.november.mcphone.api.economy.ICurrencyProvider;
import com.november.mcphone.api.economy.TxnReason;
import com.november.mcphone.api.economy.TxnResult;
import com.november.mcphone.core.script.engine.ScriptBudget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * S15e 的断言：世界级存档（{@link EconomyData}）与货币线程模型（{@link CurrencyGateway}）。
 *
 * <p>「主线程」用一条单线程执行器扮演；存档用 {@link EconomyData#toTag()} / {@link EconomyData#load} 往返，
 * 强杀就是"只剩上一次保存时那一份 tag"。计分板档要起服务器，在 S15g 的真服验收里。
 */
public class EconomyDataTest {

    static int checks = 0;
    static final List<String> failures = new ArrayList<>();

    static void eq(Object actual, Object expected, String what) {
        checks++;
        if (!Objects.equals(actual, expected)) failures.add(what + "  期望 " + expected + "，实际 " + actual);
    }

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) failures.add(what);
    }

    static final String COIN = "myserver:coin";
    static final String GEM = "myserver:gem";
    static final TxnReason RSN = new TxnReason("test", "r");

    static Currency currency(String id) {
        return new Currency(ResourceLocation.tryParse(id), Component.literal(id), "G", 2, null);
    }

    static BuiltinProvider builtin(String id, EconomyData d, TxnLog log, AtomicLong clock) {
        return new BuiltinProvider(currency(id), d, d.escrow(), log, clock::get, false, 1_000_000_000L);
    }

    static long total(EconomyData d, String id) {
        long sum = d.escrow().held(id);
        for (long v : d.all(id).values()) sum += v;
        return sum;
    }

    // ================================================================ 存档

    /** 读写往返：余额（含离线玩家）、累计、托管，一样不差；对账前后都平。 */
    static void roundTrip() throws Exception {
        AtomicLong t = new AtomicLong(1_000);
        EconomyData d = EconomyData.empty(t::get);
        TxnLog log = new TxnLog(tmp("rt"), ZoneOffset.UTC, d);
        BuiltinProvider coin = builtin(COIN, d, log, t);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), offline = UUID.randomUUID();

        eq(coin.mint(a, 1000, RSN), TxnResult.OK, "铸造");
        eq(coin.transfer(a, offline, 300, RSN), TxnResult.OK, "给一个从没上过线的 UUID 入账");
        HoldResult h = coin.hold(a, b, 200, RSN);
        eq(h.result(), TxnResult.OK, "托管");
        eq(coin.burn(a, 50, RSN), TxnResult.OK, "销毁");
        check(d.isDirty(), "改过就是脏的");
        check(EconomyAudit.run(COIN, d).balanced(), "存档前对账平：" + EconomyAudit.run(COIN, d).describe());

        EconomyData r = EconomyData.load(d.toTag(), t::get);
        eq(r.wholeLock(), null, "读回来没锁");
        check(!r.isDirty(), "刚读回来不脏");
        eq(r.get(a, COIN), 450L, "a 的余额原样回来");
        eq(r.get(offline, COIN), 300L, "离线玩家的余额原样回来 —— 按 UUID 索引，不挂玩家");
        eq(r.supply(COIN)[0], 1000L, "铸造累计原样回来");
        eq(r.supply(COIN)[1], 50L, "销毁累计原样回来");
        EscrowLedger.Entry e = r.escrow().get(h.id());
        check(e != null && e.amount() == 200 && e.beneficiary().equals(b) && !e.settled(), "托管原样回来：" + e);
        check(EconomyAudit.run(COIN, r).balanced(), "读回来对账平：" + EconomyAudit.run(COIN, r).describe());

        BuiltinProvider again = builtin(COIN, r, null, t);
        eq(again.release(h.id(), RSN), TxnResult.OK, "读回来的托管还能放款");
        eq(r.get(b, COIN), 200L, "放款到了受益人手里");
        eq(again.release(h.id(), RSN), TxnResult.ALREADY_SETTLED, "重复放款认得出");
        eq(EconomyData.load(r.toTag(), t::get).escrow().get(h.id()).settled(), true, "已结清的状态也落盘");

        coin.transfer(offline, a, 300, RSN);
        eq(d.all(COIN).containsKey(offline), false, "余额花到 0 的条目不留");
    }

    /** 多币种隔离：A 币的余额与托管不出现在 B 币的账上。 */
    static void currencyIsolation() {
        AtomicLong t = new AtomicLong(1_000);
        EconomyData d = EconomyData.empty(t::get);
        BuiltinProvider coin = builtin(COIN, d, null, t), gem = builtin(GEM, d, null, t);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        coin.mint(a, 100, RSN);
        gem.mint(a, 7, RSN);
        HoldResult h = coin.hold(a, b, 40, RSN);
        EconomyData r = EconomyData.load(d.toTag(), t::get);
        eq(r.get(a, COIN), 60L, "A 币的余额");
        eq(r.get(a, GEM), 7L, "B 币的余额，不串");
        eq(r.escrow().held(GEM), 0L, "A 币的托管不算进 B 币");
        eq(builtin(GEM, r, null, t).release(h.id(), RSN), TxnResult.UNKNOWN_ESCROW, "A 币的托管号在 B 币上放不了（S15c）");
        eq(r.get(b, GEM), 0L, "B 币一分没多");
    }

    /** 缺字段取默认、多字段忽略。 */
    static void missingAndUnknownFields() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("dataVersion", EconomyData.DATA_VERSION);
        tag.putString("fromTheFuture", "whatever");
        CompoundTag cs = new CompoundTag();
        cs.put(COIN, new CompoundTag());                 // 一种货币什么都没写
        tag.put("currencies", cs);
        ListTag esc = new ListTag();
        CompoundTag e = escrowTag(UUID.randomUUID(), COIN, 5, 1_000);
        e.remove("settled");                             // 没写 settled
        esc.add(e);
        tag.put("escrow", esc);
        EconomyData d = EconomyData.load(tag, () -> 2_000);
        eq(d.wholeLock(), null, "多出来的字段不算坏");
        eq(d.lockedCurrencies(), Set.of(), "缺字段不算坏");
        eq(d.escrow().held(COIN), 5L, "缺 settled 按没结清");
    }

    /** 版本认不得 → 整份锁住，永远不脏（不会被拿去盖原文件），一切操作 UNAVAILABLE。 */
    static void wholeLock() {
        CompoundTag noVersion = new CompoundTag();
        CompoundTag newer = new CompoundTag();
        newer.putInt("dataVersion", EconomyData.DATA_VERSION + 1);
        CompoundTag older = new CompoundTag();
        older.putInt("dataVersion", 0);
        CompoundTag badCurrencies = new CompoundTag();
        badCurrencies.putInt("dataVersion", EconomyData.DATA_VERSION);
        badCurrencies.putString("currencies", "不是表");
        CompoundTag escrowNoCurrency = new CompoundTag();
        escrowNoCurrency.putInt("dataVersion", EconomyData.DATA_VERSION);
        ListTag l = new ListTag();
        CompoundTag e = escrowTag(UUID.randomUUID(), COIN, 5, 1);
        e.remove("currency");
        l.add(e);
        escrowNoCurrency.put("escrow", l);

        Map<String, CompoundTag> cases = new java.util.LinkedHashMap<>();
        cases.put("没有 dataVersion", noVersion);
        cases.put("版本更新（新版 mod 写的）", newer);
        cases.put("版本更低且没有迁移", older);
        cases.put("currencies 不是表", badCurrencies);
        cases.put("托管读不出货币", escrowNoCurrency);
        for (var c : cases.entrySet()) {
            AtomicLong t = new AtomicLong(1);
            EconomyData d = EconomyData.load(c.getValue(), t::get);
            check(d.wholeLock() != null, c.getKey() + " → 整份锁住");
            BuiltinProvider p = builtin(COIN, d, null, t);
            UUID a = UUID.randomUUID();
            eq(p.mint(a, 10, RSN), TxnResult.UNAVAILABLE, c.getKey() + " → 铸造 UNAVAILABLE");
            eq(p.isAvailable(), false, c.getKey() + " → isAvailable 是 false");
            eq(p.unavailableReasonKey(), EconomyData.KEY_LOCKED, c.getKey() + " → 原因是存档锁住");
            d.setDirty();
            check(!d.isDirty(), c.getKey() + " → 标了脏也不脏：MC 不会拿这一份去盖原文件");
        }
    }

    /** 一种货币读坏 → 只锁那一种；它的原始数据（连同托管）原样写回；别的货币照常。 */
    static void currencyLock() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), escId = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putInt("dataVersion", EconomyData.DATA_VERSION);
        CompoundTag cs = new CompoundTag();
        CompoundTag bad = new CompoundTag();
        CompoundTag badBal = new CompoundTag();
        badBal.putString(a.toString(), "一百");         // 余额不是 long
        bad.put("balances", badBal);
        bad.putLong("minted", 100);
        cs.put(COIN, bad);
        CompoundTag good = new CompoundTag();
        CompoundTag goodBal = new CompoundTag();
        goodBal.putLong(a.toString(), 9);
        good.put("balances", goodBal);
        good.putLong("minted", 9);
        cs.put(GEM, good);
        tag.put("currencies", cs);
        ListTag esc = new ListTag();
        esc.add(escrowTag(escId, COIN, 30, 1));
        tag.put("escrow", esc);

        AtomicLong t = new AtomicLong(1);
        EconomyData d = EconomyData.load(tag, t::get);
        eq(d.wholeLock(), null, "只坏一种不锁整份");
        eq(d.lockedCurrencies(), Set.of(COIN), "锁住的只有坏的那一种");

        BuiltinProvider coin = builtin(COIN, d, null, t), gem = builtin(GEM, d, null, t);
        eq(coin.transfer(a, b, 1, RSN), TxnResult.UNAVAILABLE, "锁住的货币转不了账");
        eq(coin.transfer(a, b, 0, RSN), TxnResult.INVALID, "参数错仍然先报 INVALID（判定次序不变）");
        eq(coin.release(new EscrowId(escId), RSN), TxnResult.UNAVAILABLE,
                "锁住的货币放款是 UNAVAILABLE 而不是 UNKNOWN_ESCROW —— 号是真的，只是现在动不了");
        boolean threw = false;
        try {
            coin.balance(a);
        } catch (CurrencyUnavailableException e) {
            threw = EconomyData.KEY_LOCKED.equals(e.reasonKey());
        }
        check(threw, "锁住的货币读余额要抛，不许返回 0");
        eq(gem.transfer(a, b, 4, RSN), TxnResult.OK, "没坏的那种照常");

        CompoundTag out = d.toTag();
        eq(out.getCompound("currencies").get(COIN), bad, "坏的那一段原样写回");
        ListTag outEsc = out.getList("escrow", 10);
        check(outEsc.size() == 1 && outEsc.getCompound(0).equals(escrowTag(escId, COIN, 30, 1)),
                "坏货币的托管原样写回，没被当成能放款的读进账本：" + outEsc);
        eq(EconomyData.load(out, t::get).lockedCurrencies(), Set.of(COIN), "写回去再读还是锁着 —— 没有被一本空账盖掉");

        // 托管读坏，也锁它的货币，连余额一起原样留着
        CompoundTag t2 = new CompoundTag();
        t2.putInt("dataVersion", EconomyData.DATA_VERSION);
        CompoundTag cs2 = new CompoundTag();
        cs2.put(GEM, good.copy());
        t2.put("currencies", cs2);
        ListTag esc2 = new ListTag();
        CompoundTag brokenEntry = escrowTag(UUID.randomUUID(), GEM, 3, 1);
        brokenEntry.putString("amount", "三");
        esc2.add(brokenEntry);
        t2.put("escrow", esc2);
        EconomyData d2 = EconomyData.load(t2, t::get);
        eq(d2.lockedCurrencies(), Set.of(GEM), "托管读坏也锁那种货币");
        eq(d2.toTag().getCompound("currencies").get(GEM), good, "它的余额段原样写回，没丢");
    }

    /** 强杀：重启后看到的是上一次保存那一份完整快照 —— 总额守恒、对账平；流水里标出没进存档的几笔。 */
    static void crashRestart() throws Exception {
        AtomicLong t = new AtomicLong(1_700_000_000_000L);
        Path dir = tmp("crash");
        EconomyData d = EconomyData.empty(t::get);
        TxnLog log = new TxnLog(dir, ZoneOffset.UTC, d);
        d.onSave(() -> log.checkpoint(Instant.ofEpochMilli(t.get())));
        BuiltinProvider p = builtin(COIN, d, log, t);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        p.mint(a, 1000, RSN);
        p.hold(a, b, 100, RSN);

        CompoundTag saved = d.toTag();                     // 世界保存
        long totalAtSave = total(d, COIN);
        eq(new TxnLog(dir, ZoneOffset.UTC).noteRestart(Instant.ofEpochMilli(t.get())), 0, "刚存完，没有超前的");

        t.addAndGet(1000);
        p.transfer(a, b, 300, RSN);                        // 这两笔发生在保存之后
        p.mint(b, 50, RSN);
        p.transfer(a, b, 999_999, RSN);                    // 失败的不算"没进存档"
        // 强杀：内存里的全没了，只剩 saved

        EconomyData r = EconomyData.load(saved, t::get);
        eq(total(r, COIN), totalAtSave, "重启后总额 = 保存时的总额：没有凭空消失，也没有翻倍");
        check(EconomyAudit.run(COIN, r).balanced(), "重启后对账平：" + EconomyAudit.run(COIN, r).describe());
        eq(r.get(a, COIN), 900L, "a 回到保存时的余额");

        TxnLog after = new TxnLog(dir, ZoneOffset.UTC, r);
        eq(after.noteRestart(Instant.ofEpochMilli(t.get())), 2, "流水里标出存档点之后那 2 笔成功的变动");
        String all = String.join("\n", Files.readAllLines(dir.resolve("ledger").resolve(logName(t.get()))));
        check(all.contains("2 笔成功的变动没进存档"), "流水里写了一行说明：" + all);
        eq(after.sumMintAndBurn(COIN)[0] - r.supply(COIN)[0], 50L,
                "流水比存档多铸了 50 —— 所以对账不能读流水，要读存档里的累计");
    }

    /** 计分板档那类不碰余额的成功操作也要标脏：否则永远排在最后一个存档点之后，正常停服也会被当成没存。 */
    static void successMarksDirty() throws Exception {
        EconomyData d = EconomyData.empty(() -> 1);
        d.toTag();
        d.setDirty(false);
        TxnLog log = new TxnLog(tmp("dirty"), ZoneOffset.UTC, d);
        log.append(Instant.EPOCH, COIN, TxnLog.Kind.TRANSFER, UUID.randomUUID(), UUID.randomUUID(), 5, "-", RSN,
                TxnResult.INSUFFICIENT);
        check(!d.isDirty(), "失败的不标脏");
        log.append(Instant.EPOCH, COIN, TxnLog.Kind.TRANSFER, UUID.randomUUID(), UUID.randomUUID(), 5, "-", RSN,
                TxnResult.OK);
        check(d.isDirty(), "成功的一律标脏");
    }

    /** 对账读存档里的累计：流水被清掉（90 天）之后照样平；读流水的旧算法这时就不平了。 */
    static void auditSurvivesLogSweep() throws Exception {
        AtomicLong t = new AtomicLong(1_700_000_000_000L);
        Path dir = tmp("sweep");
        EconomyData d = EconomyData.empty(t::get);
        TxnLog log = new TxnLog(dir, ZoneOffset.UTC, d);
        BuiltinProvider p = builtin(COIN, d, log, t);
        p.mint(UUID.randomUUID(), 777, RSN);
        try (var s = Files.list(dir.resolve("ledger"))) {
            for (Path f : s.toList()) Files.delete(f);   // 90 天到了
        }
        check(EconomyAudit.run(COIN, d).balanced(), "流水清掉之后，按累计对账仍然平");
        check(!EconomyAudit.run(COIN, d, d.escrow(), log).balanced(), "按流水现算就不平了 —— 这就是不用它的理由");
    }

    /** 超时托管：退回原主并记流水；找不到 provider 的不动；很久以前已结清的清掉。 */
    static void escrowSweep() throws Exception {
        AtomicLong t = new AtomicLong(1_700_000_000_000L);
        Path dir = tmp("escrow");
        EconomyData d = EconomyData.empty(t::get);
        TxnLog log = new TxnLog(dir, ZoneOffset.UTC, d);
        BuiltinProvider coin = builtin(COIN, d, log, t), gem = builtin(GEM, d, log, t);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        coin.mint(a, 100, RSN);
        gem.mint(a, 100, RSN);
        HoldResult old = coin.hold(a, b, 30, RSN);
        HoldResult orphan = gem.hold(a, b, 20, RSN);
        HoldResult done = coin.hold(a, b, 10, RSN);
        coin.release(done.id(), RSN);

        t.addAndGet(EscrowLedger.DEFAULT_TIMEOUT_MS + 1);
        HoldResult fresh = coin.hold(a, b, 5, RSN);        // 这一笔还没到期
        EconomyRuntime.Sweep s = EconomyRuntime.sweepEscrow(d.escrow(), id -> COIN.equals(id) ? coin : null);
        eq(s.refunded(), 1, "到期的一笔退了");
        eq(s.orphaned(), 1, "找不到 provider 的一笔没动");
        eq(d.get(a, COIN), 100L - 10 - 5, "钱回到原主（只剩放出去的 10 与没到期的 5 不在手上）");
        eq(d.escrow().get(orphan.id()).settled(), false, "找不到 provider 的那笔还押着");
        eq(d.escrow().get(fresh.id()).settled(), false, "没到期的不退");
        String all = String.join("\n", Files.readAllLines(dir.resolve("ledger").resolve(logName(t.get()))));
        check(all.contains("|refund|") && all.contains("|" + EconomyRuntime.TIMEOUT_REFUND_KIND + "|"),
                "退款进了流水，kind 写明是超时：" + all);
        check(EconomyAudit.run(COIN, d).balanced(), "退完对账平");

        t.addAndGet(EscrowLedger.SETTLED_KEEP_MS);
        EconomyRuntime.sweepEscrow(d.escrow(), id -> null);
        eq(d.escrow().get(done.id()), null, "很久以前已结清的清掉了");
        check(d.escrow().get(old.id()) == null, "退款结清的那笔过了保留期也清掉");
        check(d.escrow().get(orphan.id()) != null, "没结清的不许清：钱还押在里面");
    }

    /** adapter 档：存档锁住时 hold 不能先从外部钱包扣了钱再发现记不进托管。 */
    static void adapterRespectsLock() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("dataVersion", EconomyData.DATA_VERSION + 1);
        EconomyData d = EconomyData.load(tag, () -> 1);
        Map<UUID, Long> wallet = new HashMap<>();
        UUID a = UUID.randomUUID();
        wallet.put(a, 100L);
        AdapterProvider p = new AdapterProvider(currency(COIN), new AdapterProvider.ExternalWallet() {
            public boolean available() {
                return true;
            }

            public long balance(UUID x) {
                return wallet.getOrDefault(x, 0L);
            }

            public boolean deposit(UUID x, long n) {
                wallet.merge(x, n, Long::sum);
                return true;
            }

            public boolean withdraw(UUID x, long n) {
                wallet.merge(x, -n, Long::sum);
                return true;
            }
        }, d.escrow(), 1_000);
        eq(p.hold(a, UUID.randomUUID(), 40, RSN).result(), TxnResult.UNAVAILABLE, "存档锁住，托管不了");
        eq(wallet.get(a), 100L, "外部钱包一分没扣");
    }

    // ================================================================ 网关

    /** 扮演主线程的单线程执行器。 */
    static final class Main implements AutoCloseable {
        final AtomicReference<Thread> thread = new AtomicReference<>();
        final ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
            Thread th = new Thread(r, "fake-main");
            th.setDaemon(true);
            thread.set(th);
            return th;
        });

        Main() throws Exception {
            ex.submit(() -> { }).get();
        }

        boolean on() {
            return Thread.currentThread() == thread.get();
        }

        CurrencyGateway gateway(int maxPending, long waitMs) {
            CurrencyGateway g = new CurrencyGateway(ex::execute, this::on, maxPending, waitMs * 1_000_000L);
            g.open();
            return g;
        }

        /** 让主线程卡住，直到 release。 */
        void stall(CountDownLatch release) {
            ex.execute(() -> {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                }
            });
        }

        @Override
        public void close() {
            ex.shutdownNow();
        }
    }

    static <T> T onWorker(java.util.concurrent.Callable<T> c) throws Exception {
        ExecutorService w = Executors.newSingleThreadExecutor();
        try {
            return w.submit(c).get(10, TimeUnit.SECONDS);
        } finally {
            w.shutdownNow();
        }
    }

    static String refusal(Runnable r) {
        try {
            r.run();
            return null;
        } catch (CurrencyUnavailableException e) {
            return e.reasonKey();
        }
    }

    /** 主线程上直接执行（排队等自己就是死锁）；主线程执行的操作里再调网关，也直接执行。 */
    static void gatewayInlineAndReentrant() throws Exception {
        try (Main m = new Main()) {
            CurrencyGateway g = m.gateway(8, 1_000);
            Integer v = m.ex.submit(() -> g.call(() -> g.call(() -> 42))).get(5, TimeUnit.SECONDS);
            eq(v, 42, "主线程上嵌套调用直接执行，不死锁");
            AtomicReference<Thread> ran = new AtomicReference<>();
            Integer w = onWorker(() -> g.call(() -> {
                ran.set(Thread.currentThread());
                return 7;
            }));
            eq(w, 7, "worker 上调用拿到结果");
            eq(ran.get(), m.thread.get(), "操作是在主线程上执行的");
            RuntimeException boom = new IllegalArgumentException("x");
            Throwable got = null;
            try {
                onWorker(() -> g.call(() -> {
                    throw boom;
                }));
            } catch (java.util.concurrent.ExecutionException e) {
                got = e.getCause();
            }
            eq(got, boom, "操作抛的原样抛回 worker");
        }
    }

    /** 主线程卡住超过单次等待 → UNAVAILABLE（主线程忙），而且这个操作之后永远不会被执行。 */
    static void gatewayTimeoutNeverRunsLater() throws Exception {
        try (Main m = new Main()) {
            CurrencyGateway g = m.gateway(8, 100);
            CountDownLatch release = new CountDownLatch(1);
            m.stall(release);
            AtomicBoolean ran = new AtomicBoolean();
            String why = onWorker(() -> refusal(() -> g.call(() -> {
                ran.set(true);
                return 1;
            })));
            eq(why, CurrencyGateway.KEY_BUSY, "超时回 UNAVAILABLE，原因是主线程忙");
            release.countDown();
            m.ex.submit(() -> { }).get(5, TimeUnit.SECONDS);   // 主线程把积压的都跑完
            check(!ran.get(), "超时的那个之后没有被执行 —— 否则调用方以为没转成，钱却转出去了");
            eq(g.pending(), 0, "排队计数归零");
        }
    }

    /** 超时与主线程开始执行撞在同一刻：二者只能成立一个 —— 要么拿到结果且只执行一次，要么被拒且从没执行。 */
    static void gatewayTimeoutRace() throws Exception {
        try (Main m = new Main()) {
            CurrencyGateway g = m.gateway(64, 2);
            int bad = 0;
            for (int i = 0; i < 300; i++) {
                AtomicInteger runs = new AtomicInteger();
                CountDownLatch release = new CountDownLatch(1);
                m.stall(release);
                ExecutorService w = Executors.newSingleThreadExecutor();
                try {
                    Future<String> f = w.submit(() -> refusal(() -> g.call(() -> {
                        runs.incrementAndGet();
                        return 1;
                    })));
                    // 在 2 ms 的边上放开主线程
                    java.util.concurrent.locks.LockSupport.parkNanos(500_000L + (i % 7) * 400_000L);
                    release.countDown();
                    String why = f.get(5, TimeUnit.SECONDS);
                    m.ex.submit(() -> { }).get(5, TimeUnit.SECONDS);
                    if (why == null && runs.get() != 1) bad++;
                    if (why != null && runs.get() != 0) bad++;
                } finally {
                    w.shutdownNow();
                }
            }
            eq(bad, 0, "300 次擦边：没有一次既被拒又执行了、或者执行了两次");
        }
    }

    /** 排队满 → UNAVAILABLE（排队满），不是 FAILED。 */
    static void gatewayQueueFull() throws Exception {
        try (Main m = new Main()) {
            CurrencyGateway g = m.gateway(2, 2_000);
            CountDownLatch release = new CountDownLatch(1);
            m.stall(release);
            ExecutorService w = Executors.newFixedThreadPool(3);
            List<Future<String>> fs = new ArrayList<>();
            for (int i = 0; i < 2; i++) fs.add(w.submit(() -> refusal(() -> g.call(() -> 1))));
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (g.pending() < 2 && System.nanoTime() < deadline) Thread.sleep(1);
            String third = w.submit(() -> refusal(() -> g.call(() -> 1))).get(5, TimeUnit.SECONDS);
            eq(third, CurrencyGateway.KEY_QUEUE_FULL, "第三个排不上：UNAVAILABLE，原因是排队满");
            release.countDown();
            for (Future<String> f : fs) eq(f.get(5, TimeUnit.SECONDS), null, "排上的两个照常拿到结果");
            w.shutdownNow();
        }
    }

    /** 停服：worker 正等着主线程，close 之后立刻返回 UNAVAILABLE，操作永远不会被执行；关了之后再调直接拒。 */
    static void gatewayCloseReleasesWaiters() throws Exception {
        try (Main m = new Main()) {
            CurrencyGateway g = m.gateway(8, 30_000);
            CountDownLatch release = new CountDownLatch(1);
            m.stall(release);
            AtomicBoolean ran = new AtomicBoolean();
            ExecutorService w = Executors.newSingleThreadExecutor();
            Future<String> f = w.submit(() -> refusal(() -> g.call(() -> {
                ran.set(true);
                return 1;
            })));
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (g.pending() < 1 && System.nanoTime() < deadline) Thread.sleep(1);
            long t0 = System.nanoTime();
            g.close();
            String why = f.get(5, TimeUnit.SECONDS);
            long waitedMs = (System.nanoTime() - t0) / 1_000_000;
            eq(why, CurrencyGateway.KEY_CLOSED, "停服：UNAVAILABLE，原因是正在停");
            check(waitedMs < 1_000, "close 之后立刻返回，没有白等 30 秒的超时（等了 " + waitedMs + " ms）");
            release.countDown();
            m.ex.submit(() -> { }).get(5, TimeUnit.SECONDS);
            check(!ran.get(), "取消掉的那个之后没有被执行");
            eq(onWorker(() -> refusal(() -> g.call(() -> 1))), CurrencyGateway.KEY_CLOSED, "关了之后再调直接拒");
            w.shutdownNow();
        }
    }

    /**
     * 「主线程等 worker」：停服时主线程要等 worker 停下（ScriptWorkers.stop 等 5 秒）。
     * 先关网关再等 → worker 立刻出来；不关直接等 → 也只会等到单次超时，不会无限卡住。
     */
    static void gatewayMainWaitsOnWorker() throws Exception {
        for (boolean closeFirst : new boolean[]{true, false}) {
            try (Main m = new Main()) {
                CurrencyGateway g = m.gateway(8, 300);
                CountDownLatch inCall = new CountDownLatch(1);
                java.util.concurrent.CompletableFuture<Future<String>> worker = new java.util.concurrent.CompletableFuture<>();
                // 主线程先进"停服"：不再处理任务，而是等 worker 结束。worker 的调用只能排在它后面
                Future<Long> stopping = m.ex.submit(() -> {
                    Future<String> f = worker.get(10, TimeUnit.SECONDS);
                    inCall.await();
                    while (g.pending() < 1) Thread.sleep(1);
                    if (closeFirst) g.close();
                    long t0 = System.nanoTime();
                    f.get(10, TimeUnit.SECONDS);
                    return (System.nanoTime() - t0) / 1_000_000;
                });
                ExecutorService w = Executors.newSingleThreadExecutor();
                worker.complete(w.submit(() -> {
                    inCall.countDown();
                    return refusal(() -> g.call(() -> 1));
                }));
                long waited = stopping.get(10, TimeUnit.SECONDS);
                String why = worker.get().get();
                if (closeFirst) {
                    eq(why, CurrencyGateway.KEY_CLOSED, "先关网关：worker 拿到正在停");
                    check(waited < 200, "先关网关：主线程几乎不用等（" + waited + " ms）");
                } else {
                    eq(why, CurrencyGateway.KEY_BUSY, "没关网关：worker 等到单次超时");
                    check(waited < 2_000, "没关网关：也只等单次超时，不是死锁（" + waited + " ms）");
                }
                w.shutdownNow();
            }
        }
    }

    /** worker 在等的时候被打断（shutdownNow）：立刻回 UNAVAILABLE，打断标记保留，操作不会被执行。 */
    static void gatewayInterrupt() throws Exception {
        try (Main m = new Main()) {
            CurrencyGateway g = m.gateway(8, 30_000);
            CountDownLatch release = new CountDownLatch(1);
            m.stall(release);
            AtomicBoolean ran = new AtomicBoolean(), stillInterrupted = new AtomicBoolean();
            AtomicReference<String> why = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                why.set(refusal(() -> g.call(() -> {
                    ran.set(true);
                    return 1;
                })));
                stillInterrupted.set(Thread.currentThread().isInterrupted());
            });
            worker.start();
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (g.pending() < 1 && System.nanoTime() < deadline) Thread.sleep(1);
            worker.interrupt();
            worker.join(5_000);
            eq(why.get(), CurrencyGateway.KEY_INTERRUPTED, "被打断：UNAVAILABLE，原因是被打断");
            check(stillInterrupted.get(), "打断标记还在，交给上层去停");
            release.countDown();
            m.ex.submit(() -> { }).get(5, TimeUnit.SECONDS);
            check(!ran.get(), "被打断的那个之后没有被执行");
        }
    }

    /** 服务器停了之后 execute() 会就地在调用方线程上跑：网关绝不能因此在 worker 上碰账。执行器直接拒也一样。 */
    static void gatewayExecutorQuirks() throws Exception {
        AtomicBoolean ran = new AtomicBoolean();
        CurrencyGateway inline = new CurrencyGateway(Runnable::run, () -> false, 8, 1_000_000_000L);
        inline.open();
        eq(onWorker(() -> refusal(() -> inline.call(() -> {
            ran.set(true);
            return 1;
        }))), CurrencyGateway.KEY_CLOSED, "就地执行的执行器：拒，原因是正在停");
        check(!ran.get(), "没有在 worker 上执行");

        CurrencyGateway rejecting = new CurrencyGateway(r -> {
            throw new java.util.concurrent.RejectedExecutionException();
        }, () -> false, 8, 1_000_000_000L);
        rejecting.open();
        eq(onWorker(() -> refusal(() -> rejecting.call(() -> 1))), CurrencyGateway.KEY_CLOSED, "执行器拒收：拒，原因是正在停");
        eq(rejecting.pending(), 0, "拒收之后排队计数不漏");
    }

    /** 等主线程的时间不算墙钟；一次求值里等的累计有上限，用完了直接拒。 */
    static void gatewayBudget() throws Exception {
        try (Main m = new Main()) {
            CurrencyGateway g = m.gateway(8, 1_000);
            AtomicReference<String> out = new AtomicReference<>("");
            onWorker(() -> {
                eq(ScriptBudget.hostWaitLeftNanos(), Long.MAX_VALUE, "不在求值里，等宿主不限");
                ScriptBudget b = new ScriptBudget(1_000_000, 20_000_000L, 150_000_000L);
                b.begin();
                try {
                    CountDownLatch release = new CountDownLatch(1);
                    m.stall(release);
                    new Thread(() -> {
                        try {
                            Thread.sleep(60);
                        } catch (InterruptedException ignored) {
                        }
                        release.countDown();
                    }).start();
                    long wallBefore = ScriptBudget.wallLeftNanos();
                    g.call(() -> 1);                                   // 主线程卡了约 60 ms
                    long wallAfter = ScriptBudget.wallLeftNanos();
                    check(wallAfter > wallBefore - 10_000_000L,
                            "等主线程的 60 ms 没有吃掉 20 ms 的墙钟（前 " + wallBefore / 1_000_000 + " ms，后 "
                                    + wallAfter / 1_000_000 + " ms）");
                    check(ScriptBudget.hostWaitLeftNanos() < 150_000_000L - 40_000_000L, "等的时间记进了累计");

                    CountDownLatch never = new CountDownLatch(1);
                    m.stall(never);
                    out.set(refusal(() -> g.call(() -> 1)));           // 剩下的额度等完就超时
                    String next = refusal(() -> g.call(() -> 1));
                    never.countDown();
                    eq(next, CurrencyGateway.KEY_WAIT_BUDGET, "累计用完：直接拒，原因是这一次求值等得太久了");
                } finally {
                    b.end();
                }
                return null;
            });
            eq(out.get(), CurrencyGateway.KEY_BUSY, "额度剩多少就等多少，等完是主线程忙");
        }
    }

    /** 调用方 App 的章：worker 上盖的，到主线程执行时还在；执行完主线程原来的章原样恢复。 */
    static void gatewayCarriesCallingApp() throws Exception {
        try (Main m = new Main()) {
            CurrencyGateway g = m.gateway(8, 1_000);
            m.ex.submit(() -> CallingApp.enter("main.own")).get();
            String seen = onWorker(() -> {
                CallingApp.enter("app.shop");
                try {
                    return g.call(CallingApp::current);
                } finally {
                    CallingApp.leave();
                }
            });
            eq(seen, "app.shop", "主线程上执行时看到的是发起调用的 App");
            eq(m.ex.submit(CallingApp::current).get(), "main.own", "主线程自己的章执行完原样恢复");
        }
    }

    /** 包装层覆盖了接口的每一个方法：漏一个就多一条绕过网关直达 provider 的路。 */
    static void gatedCoversInterface() {
        List<String> missing = new ArrayList<>();
        for (Method im : ICurrencyProvider.class.getMethods()) {
            try {
                Method gm = GatedCurrencyProvider.class.getMethod(im.getName(), im.getParameterTypes());
                if (gm.getDeclaringClass() != GatedCurrencyProvider.class) missing.add(im.getName());
            } catch (NoSuchMethodException e) {
                missing.add(im.getName());
            }
        }
        eq(missing, List.of(), "GatedCurrencyProvider 自己实现了 ICurrencyProvider 的全部方法");
    }

    /** 注册表交出去的都是包过网关的；查重、幂等照旧；被拒时 TxnResult 是 UNAVAILABLE，balance 抛。 */
    static void registryHandsOutGated() throws Exception {
        AtomicLong t = new AtomicLong(1);
        EconomyData d = EconomyData.empty(t::get);
        BuiltinProvider raw = builtin(COIN, d, null, t);
        CurrencyGateway closed = new CurrencyGateway(Runnable::run, () -> false);
        CurrencyRegistry reg = new CurrencyRegistry(closed);
        check(reg.register(raw, true), "登记");
        check(reg.get(COIN) instanceof GatedCurrencyProvider, "交出去的是包过网关的");
        check(reg.register(raw, false), "同一个实例再登记是幂等的");
        check(!reg.register(builtin(COIN, d, null, t), false), "同一种货币第二个实例照样拒");
        ICurrencyProvider p = reg.get(COIN);
        eq(onWorker(() -> p.transfer(UUID.randomUUID(), UUID.randomUUID(), 1, RSN)), TxnResult.UNAVAILABLE,
                "网关没开：UNAVAILABLE");
        eq(onWorker(p::unavailableReasonKey), CurrencyGateway.KEY_CLOSED, "原因读得到");
        eq(onWorker(() -> p.hold(UUID.randomUUID(), UUID.randomUUID(), 1, RSN).result()), TxnResult.UNAVAILABLE,
                "托管也是 UNAVAILABLE");
        eq(onWorker(() -> refusal(() -> p.balance(UUID.randomUUID()))), CurrencyGateway.KEY_CLOSED,
                "读余额被拒要抛，不许返回 0");
    }

    /** 1000 次合法调用经网关、两条 worker 并发：总额不变，对账平（builtin 档；计分板档要真服，在 S15g）。 */
    static void conservationThroughGateway() throws Exception {
        try (Main m = new Main()) {
            CurrencyGateway g = m.gateway(256, 5_000);
            AtomicLong t = new AtomicLong(1);
            EconomyData d = EconomyData.empty(t::get);
            CurrencyRegistry reg = new CurrencyRegistry(g);
            // 照生产的样子接上流水：铸造、销毁的累计是经它记进存档的
            reg.register(builtin(COIN, d, new TxnLog(tmp("cons"), ZoneOffset.UTC, d), t), true);
            ICurrencyProvider p = reg.get(COIN);
            UUID[] ps = new UUID[5];
            for (int i = 0; i < ps.length; i++) ps[i] = UUID.randomUUID();
            for (UUID u : ps) eq(onWorker(() -> p.mint(u, 1000, RSN)), TxnResult.OK, "发钱");
            long before = m.ex.submit(() -> total(d, COIN)).get();
            ExecutorService w = Executors.newFixedThreadPool(2);
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                int k = i;
                fs.add(w.submit(() -> {
                    UUID from = ps[k % ps.length], to = ps[(k + 1 + k / ps.length) % ps.length];
                    if (from.equals(to)) to = ps[(k + 2) % ps.length];
                    if (k % 10 == 0) {
                        HoldResult h = p.hold(from, to, 3, RSN);
                        if (h.result() == TxnResult.OK) p.release(h.id(), RSN);
                    } else {
                        p.transfer(from, to, 1 + k % 17, RSN);
                    }
                }));
            }
            for (Future<?> f : fs) f.get(30, TimeUnit.SECONDS);
            w.shutdownNow();
            eq(m.ex.submit(() -> total(d, COIN)).get(), before, "1000 次合法调用后总额不变");
            check(m.ex.submit(() -> EconomyAudit.run(COIN, d).balanced()).get(), "对账平");
        }
    }

    // ================================================================ 工具

    static CompoundTag escrowTag(UUID id, String currency, long amount, long createdAt) {
        CompoundTag e = new CompoundTag();
        e.putString("id", id.toString());
        e.putString("owner", UUID.nameUUIDFromBytes(new byte[]{1}).toString());
        e.putString("beneficiary", UUID.nameUUIDFromBytes(new byte[]{2}).toString());
        e.putString("currency", currency);
        e.putLong("amount", amount);
        e.putLong("createdAt", createdAt);
        e.putBoolean("settled", false);
        return e;
    }

    static Path tmp(String name) throws Exception {
        Path p = Files.createTempDirectory("mcphone-s15e-" + name);
        p.toFile().deleteOnExit();
        return p;
    }

    static String logName(long millis) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .format(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)) + ".log";
    }

    public static void main(String[] args) throws Exception {
        roundTrip();
        currencyIsolation();
        missingAndUnknownFields();
        wholeLock();
        currencyLock();
        crashRestart();
        successMarksDirty();
        auditSurvivesLogSweep();
        escrowSweep();
        adapterRespectsLock();
        gatewayInlineAndReentrant();
        gatewayTimeoutNeverRunsLater();
        gatewayTimeoutRace();
        gatewayQueueFull();
        gatewayCloseReleasesWaiters();
        gatewayMainWaitsOnWorker();
        gatewayInterrupt();
        gatewayExecutorQuirks();
        gatewayBudget();
        gatewayCarriesCallingApp();
        gatedCoversInterface();
        registryHandsOutGated();
        conservationThroughGateway();

        System.out.println("断言 " + checks + " 条");
        if (!failures.isEmpty()) {
            System.out.println("失败 " + failures.size() + " 条：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }
}
