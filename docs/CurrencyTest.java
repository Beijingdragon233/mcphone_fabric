package com.november.mcphone.api.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.november.mcphone.core.script.server.economy.Amounts;
import com.november.mcphone.core.script.server.economy.BalanceStore;
import com.november.mcphone.core.script.server.economy.BuiltinProvider;
import com.november.mcphone.core.script.server.economy.CurrencyRegistry;
import com.november.mcphone.core.script.server.economy.EconomyAudit;
import com.november.mcphone.core.script.server.economy.LegacyWalletProvider;
import com.november.mcphone.core.script.server.economy.EscrowLedger;
import com.november.mcphone.core.script.server.economy.TxnLog;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 货币 SDK 的契约（施工方案 §22.9 的五条不变量、§22.11）。
 *
 * <p><b>这里测的是规则，不是发布出去的那个实现</b> —— {@code ICurrencyProvider} 的实现是 S15 的交付物。
 * 本步交付的是契约，而契约里真正能出错的部分（金额判定、溢出、守恒、格式化）都收在
 * {@link Balances} 里，各实现照着调就不会各漏各的。下面那个 {@code Ledger} 是一份参照实现，
 * 证明这套规则自洽、且守恒。
 *
 * <p><b>这里测不了的</b>：托管落盘、流水文件、跨重启 —— 都要服务端（S15）。
 *
 * <p>跑法：{@code ./gradlew assertTests}（在 {@code platforms/<目标名>/} 下）。
 */
public class CurrencyTest {

    static int checks = 0;
    static final List<String> failures = new ArrayList<>();

    static void eq(Object actual, Object expected, String what) {
        checks++;
        if (!java.util.Objects.equals(actual, expected)) {
            failures.add(what + "  期望 " + expected + "，实际 " + actual);
        }
    }

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) failures.add(what);
    }

    static String rejects(Runnable body) {
        try {
            body.run();
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    // ---------------------------------------------------------------- §22.9 不变量

    static void amountMustBePositive() {
        eq(Balances.checkAmount(1), TxnResult.OK, "正数");
        eq(Balances.checkAmount(0), TxnResult.INVALID, "0 拒 —— 只是往流水里灌垃圾");
        eq(Balances.checkAmount(-1), TxnResult.INVALID, "负数拒");
        eq(Balances.checkAmount(Long.MIN_VALUE), TxnResult.INVALID, "最小值也拒");

        // 这是最经典的那个洞：pay(to, -1000) 实现成"from 减 amount、to 加 amount"就是反向偷钱
        eq(Balances.checkDebit(0, -1000, false), TxnResult.INVALID, "负数扣款先被金额判据拦住");
        eq(Balances.checkCredit(0, -1000, 1_000_000), TxnResult.INVALID, "负数入账同样");
    }

    static void ceilingDoesNotWrap() {
        eq(Balances.checkCredit(900, 100, 1000), TxnResult.OK, "刚好到顶");
        eq(Balances.checkCredit(900, 101, 1000), TxnResult.LIMIT, "超一点就 LIMIT");
        eq(Balances.checkCredit(Long.MAX_VALUE, 1, Long.MAX_VALUE), TxnResult.LIMIT,
                "加法溢出返回 LIMIT，不许回绕 —— 回绕会让余额爆表的账户收一笔钱之后变成负数");
        eq(Balances.checkCredit(Long.MAX_VALUE - 1, 2, Long.MAX_VALUE), TxnResult.LIMIT, "差一点溢出");
    }

    static void debits() {
        eq(Balances.checkDebit(1000, 1000, false), TxnResult.OK, "刚好够");
        eq(Balances.checkDebit(999, 1000, false), TxnResult.INSUFFICIENT, "差一点");
        eq(Balances.checkDebit(0, 1000, true), TxnResult.OK, "允许负余额时透支放行");
        eq(Balances.checkDebit(Long.MIN_VALUE + 1, 2, true), TxnResult.LIMIT, "允许负数也不许绕到正数去");
    }

    // ---------------------------------------------------------------- 显示与解析

    static void formatParse() {
        eq(Balances.format(1234, 2), "12.34", "§22.3 ② 举的那个例子");
        eq(Balances.format(0, 2), "0.00", "零");
        eq(Balances.format(5, 2), "0.05", "不足一位要补零");
        eq(Balances.format(-5, 2), "-0.05", "负数的补零在绝对值上做，不然会拼出 -0.-5");
        eq(Balances.format(1234, 0), "1234", "没有小数位就是整数");
        eq(Balances.format(Long.MAX_VALUE, 2), "92233720368547758.07", "long 的顶");

        eq(Balances.parse("12.34", 2), 1234L, "往返");
        eq(Balances.parse("12", 2), 1200L, "省略小数位补零");
        eq(Balances.parse("12.3", 2), 1230L, "少写一位补零");
        eq(Balances.parse("-12.34", 2), -1234L, "负数");
        eq(Balances.parse(".5", 2), 50L, "省略整数位");
        check(rejects(() -> Balances.parse("12.345", 2)) != null,
                "小数位多于 decimals 要抛 —— 悄悄抹掉一位就是悄悄改了金额");
        check(rejects(() -> Balances.parse("abc", 2)) != null, "不是十进制拒");
        check(rejects(() -> Balances.parse(null, 2)) != null, "null 拒");
        check(rejects(() -> Balances.parse("", 2)) != null, "空串拒");

        for (long v : new long[]{0, 1, 99, 100, 12345, -1, -12345, Long.MAX_VALUE}) {
            eq(Balances.parse(Balances.format(v, 2), 2), v, "format→parse 往返 " + v);
        }
    }

    // ---------------------------------------------------------------- 值类型

    static void txnReason() {
        eq(new TxnReason("market:buy", "order-1").kind(), "market:buy", "kind");
        eq(new TxnReason(null, null).ref(), "", "null 归一成空串");
        check(rejects(() -> new TxnReason("a|b", "x")) != null,
                "竖线拒 —— 流水行按竖线分隔，放进去就能伪造一列");
        check(rejects(() -> new TxnReason("a\nb", "x")) != null,
                "换行拒 —— 一个换行就能在流水里伪造出一整行不存在的交易");
        String withNul = "x" + (char) 0 + "y";
        check(rejects(() -> new TxnReason("a", withNul)) != null, "控制字符拒");
        check(rejects(() -> new TxnReason("x".repeat(TxnReason.MAX_KIND + 1), "y")) != null, "kind 超长拒");
        check(rejects(() -> new TxnReason("y", "x".repeat(TxnReason.MAX_REF + 1))) != null, "ref 超长拒");

        // 没有 appId 这一格：调用者自报身份 = 审计作废
        eq(TxnReason.class.getRecordComponents().length, 2, "只有 kind 与 ref，appId 由宿主盖章");
    }

    static void holdResult() {
        EscrowId id = new EscrowId(UUID.randomUUID());
        eq(HoldResult.ok(id).result(), TxnResult.OK, "成功");
        eq(HoldResult.ok(id).id(), id, "成功带号");
        eq(HoldResult.fail(TxnResult.INSUFFICIENT).id(), null, "失败不带号");
        check(rejects(() -> new HoldResult(TxnResult.OK, null)) != null, "OK 必须带号");
        check(rejects(() -> new HoldResult(TxnResult.FAILED, id)) != null, "失败不许带号");
        check(rejects(() -> new EscrowId(null)) != null, "托管号不能为 null");
    }

    /** §23.4：不许复用错误码。这几个必须各自存在，别合并。 */
    static void distinctCodes() {
        for (String name : new String[]{"OK", "INSUFFICIENT", "LIMIT", "UNAVAILABLE", "INVALID",
                "NOT_AUTHORIZED", "UNKNOWN_ESCROW", "ALREADY_SETTLED", "FAILED"}) {
            boolean found = false;
            for (TxnResult r : TxnResult.values()) if (r.name().equals(name)) found = true;
            check(found, "TxnResult 要有 " + name);
        }
        check(TxnResult.INVALID != TxnResult.ALREADY_SETTLED,
                "「已经结过了」不许与「金额非法」共用一个码：调用方分不出该重试还是该报错");
        check(TxnResult.FAILED != TxnResult.UNKNOWN_ESCROW,
                "「不是本 provider 的托管号」不许落进 FAILED：多货币服上这条路一定会走到");
    }

    // ---------------------------------------------------------------- 参照实现：守恒

    /** 一份最小的账本，只用来证明这套规则自洽。真正的实现是 S15 的事。 */
    static final class Ledger {
        final Map<UUID, Long> bal = new HashMap<>();
        final Map<UUID, long[]> escrow = new HashMap<>();   // id → {金额, 已结算标志}
        final Map<UUID, UUID> owner = new HashMap<>();
        final Map<UUID, UUID> beneficiary = new HashMap<>();
        final long max;

        Ledger(long max) {
            this.max = max;
        }

        long get(UUID p) {
            return bal.getOrDefault(p, 0L);
        }

        TxnResult mint(UUID to, long amt) {
            TxnResult r = Balances.checkCredit(get(to), amt, max);
            if (r != TxnResult.OK) return r;
            bal.put(to, get(to) + amt);
            return TxnResult.OK;
        }

        TxnResult transfer(UUID from, UUID to, long amt) {
            TxnResult r = Balances.checkDebit(get(from), amt, false);
            if (r != TxnResult.OK) return r;
            r = Balances.checkCredit(get(to), amt, max);
            if (r != TxnResult.OK) return r;
            // 两端在同一步里改完：中间态不许存在（§22.3 ④）
            bal.put(from, get(from) - amt);
            bal.put(to, get(to) + amt);
            return TxnResult.OK;
        }

        HoldResult hold(UUID from, UUID to, long amt) {
            TxnResult r = Balances.checkDebit(get(from), amt, false);
            if (r != TxnResult.OK) return HoldResult.fail(r);
            bal.put(from, get(from) - amt);
            UUID id = UUID.randomUUID();
            escrow.put(id, new long[]{amt, 0});
            owner.put(id, from);
            beneficiary.put(id, to);
            return HoldResult.ok(new EscrowId(id));
        }

        TxnResult settle(EscrowId id, boolean toBeneficiary) {
            long[] e = escrow.get(id.value());
            if (e == null) return TxnResult.UNKNOWN_ESCROW;
            if (e[1] != 0) return TxnResult.ALREADY_SETTLED;
            UUID target = toBeneficiary ? beneficiary.get(id.value()) : owner.get(id.value());
            TxnResult r = Balances.checkCredit(get(target), e[0], max);
            if (r != TxnResult.OK) return r;
            bal.put(target, get(target) + e[0]);
            e[1] = 1;
            return TxnResult.OK;
        }

        /** 所有余额 + 托管中的钱。除 mint / burn 外这个数不许变（§22.9）。 */
        long total() {
            long t = 0;
            for (long v : bal.values()) t += v;
            for (Map.Entry<UUID, long[]> e : escrow.entrySet()) if (e.getValue()[1] == 0) t += e.getValue()[0];
            return t;
        }
    }

    static void conservation() {
        Ledger l = new Ledger(1_000_000);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();

        eq(l.mint(a, 10_000), TxnResult.OK, "铸 100.00 给 A");
        eq(l.total(), 10_000L, "总量");

        eq(l.transfer(a, b, 2_500), TxnResult.OK, "转账成功");
        eq(l.get(a), 7_500L, "付款方扣了");
        eq(l.get(b), 2_500L, "收款方加了");
        eq(l.total(), 10_000L, "转账不改变总量");

        eq(l.transfer(a, b, 999_999), TxnResult.INSUFFICIENT, "不够就不动");
        eq(l.total(), 10_000L, "失败的转账一分钱都没动");
        eq(l.get(a), 7_500L, "失败后付款方原样");

        eq(l.transfer(a, b, -1000), TxnResult.INVALID, "负数转账拒");
        eq(l.get(b), 2_500L, "被偷方原样 —— 这条不过就是最经典的那个洞");

        // 托管：钱离开 A 但还在系统里
        HoldResult h = l.hold(a, b, 5_000);
        eq(h.result(), TxnResult.OK, "托管成功");
        eq(l.get(a), 2_500L, "托管当场扣款");
        eq(l.total(), 10_000L, "托管中的钱还算在总量里 —— 不然对账会少一笔");

        eq(l.settle(h.id(), true), TxnResult.OK, "放款");
        eq(l.get(b), 7_500L, "受益人收到");
        eq(l.total(), 10_000L, "放款不改变总量");
        eq(l.settle(h.id(), true), TxnResult.ALREADY_SETTLED, "重复放款认得出来，不是 FAILED");
        eq(l.settle(new EscrowId(UUID.randomUUID()), true), TxnResult.UNKNOWN_ESCROW, "不认识的托管号");
        eq(l.total(), 10_000L, "两次失败的结算都没动钱");

        // 退款走的是原主那条路
        HoldResult h2 = l.hold(b, a, 1_000);
        eq(l.settle(h2.id(), false), TxnResult.OK, "退款");
        eq(l.get(b), 7_500L, "退回原主");
        eq(l.total(), 10_000L, "退款不改变总量");
    }

    // ================================================================ 真 provider（§22.7 builtin）

    /** 内存里的余额表。真正的落在 PhonePlayerData.economy()，那要服务器。 */
    static final class MemBalances implements BalanceStore {
        final java.util.Map<String, Long> m = new ConcurrentHashMap<>();

        static String k(UUID p, String c) {
            return p + "/" + c;
        }

        public long get(UUID p, String c) {
            return m.getOrDefault(k(p, c), 0L);
        }

        public void set(UUID p, String c, long v) {
            m.put(k(p, c), v);
        }

        public java.util.Map<UUID, Long> all(String c) {
            java.util.Map<UUID, Long> out = new java.util.HashMap<>();
            for (var e : m.entrySet()) {
                String[] parts = e.getKey().split("/", 2);
                if (parts.length == 2 && parts[1].equals(c)) out.put(UUID.fromString(parts[0]), e.getValue());
            }
            return out;
        }
    }

    static Currency coin() {
        return new Currency(ResourceLocation.tryParse("myserver:coin"),
                Component.literal("金币"), "G", 2, null);
    }

    static Path tmpDir(String name) throws Exception {
        Path p = Files.createTempDirectory("mcphone-econ-" + name);
        p.toFile().deleteOnExit();
        return p;
    }

    static BuiltinProvider provider(MemBalances bal, EscrowLedger esc, TxnLog log, AtomicLong clock, long max) {
        return new BuiltinProvider(coin(), bal, esc, log, clock::get, false, max);
    }

    /** §22.9：amount ≤ 0 一律 INVALID，而且【被拒后两侧余额都不变】。 */
    static void nonPositiveRejected() throws Exception {
        MemBalances bal = new MemBalances();
        AtomicLong t = new AtomicLong(0);
        EscrowLedger esc = new EscrowLedger(t::get);
        TxnLog log = new TxnLog(tmpDir("np"), java.time.ZoneId.of("UTC"));
        BuiltinProvider p = provider(bal, esc, log, t, 1_000_000);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        p.mint(a, 10_000, new TxnReason("test", "1"));
        p.mint(b, 5_000, new TxnReason("test", "2"));

        for (long bad : new long[]{0, -1, -1000, Long.MIN_VALUE}) {
            eq(p.transfer(a, b, bad, new TxnReason("test", "x")), TxnResult.INVALID, "转账 " + bad + " 拒");
            eq(p.balance(a), 10_000L, "被拒后付款方原样（" + bad + "）");
            eq(p.balance(b), 5_000L, "被拒后收款方原样 —— 这条不过就是反向偷钱（" + bad + "）");
        }
    }

    /** §22.9：超上限与 Long.MAX_VALUE 都返回 LIMIT，不回绕。 */
    static void ceilingAndOverflow() throws Exception {
        MemBalances bal = new MemBalances();
        AtomicLong t = new AtomicLong(0);
        TxnLog log = new TxnLog(tmpDir("lim"), java.time.ZoneId.of("UTC"));
        BuiltinProvider p = provider(bal, new EscrowLedger(t::get), log, t, 1_000);
        UUID a = UUID.randomUUID();
        eq(p.mint(a, 1_000, new TxnReason("t", "1")), TxnResult.OK, "刚好到顶");
        eq(p.mint(a, 1, new TxnReason("t", "2")), TxnResult.LIMIT, "超一点就 LIMIT");
        eq(p.balance(a), 1_000L, "被拒之后余额不动");

        BuiltinProvider big = provider(bal, new EscrowLedger(t::get), log, t, Long.MAX_VALUE);
        UUID c = UUID.randomUUID();
        big.mint(c, Long.MAX_VALUE, new TxnReason("t", "3"));
        eq(big.mint(c, 1, new TxnReason("t", "4")), TxnResult.LIMIT, "Long.MAX_VALUE 再加一是 LIMIT，不回绕");
        check(big.balance(c) > 0, "没有绕成负数");
    }

    /** §22.12：1000 次并发 transfer 之后总额不变。 */
    static void concurrentConservation() throws Exception {
        MemBalances bal = new MemBalances();
        AtomicLong t = new AtomicLong(0);
        TxnLog log = new TxnLog(tmpDir("conc"), java.time.ZoneId.of("UTC"));
        BuiltinProvider p = provider(bal, new EscrowLedger(t::get), log, t, Long.MAX_VALUE);

        UUID[] players = new UUID[8];
        for (int i = 0; i < players.length; i++) {
            players[i] = UUID.randomUUID();
            p.mint(players[i], 100_000, new TxnReason("t", "seed" + i));
        }
        long before = 0;
        for (UUID u : players) before += p.balance(u);

        int threads = 8, perThread = 125;                   // 8 × 125 = 1000
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            final int seed = i;
            Thread th = new Thread(() -> {
                try {
                    go.await();
                    java.util.Random r = new java.util.Random(seed);
                    for (int k = 0; k < perThread; k++) {
                        UUID from = players[r.nextInt(players.length)];
                        UUID to = players[r.nextInt(players.length)];
                        if (from.equals(to)) continue;
                        p.transfer(from, to, 1 + r.nextInt(100), new TxnReason("t", "c" + k));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            th.setDaemon(true);
            th.start();
        }
        go.countDown();
        check(done.await(30, java.util.concurrent.TimeUnit.SECONDS), "1000 次并发转账跑完了");

        long after = 0;
        for (UUID u : players) after += p.balance(u);
        eq(after, before, "1000 次并发转账之后总额不变（守恒）");
        for (UUID u : players) check(p.balance(u) >= 0, "没有谁变成负数");
    }

    /** §22.4：托管受益人此刻定死；重复释放/退款要认得出来。 */
    static void escrow() throws Exception {
        MemBalances bal = new MemBalances();
        AtomicLong t = new AtomicLong(0);
        EscrowLedger esc = new EscrowLedger(t::get);
        TxnLog log = new TxnLog(tmpDir("esc"), java.time.ZoneId.of("UTC"));
        BuiltinProvider p = provider(bal, esc, log, t, Long.MAX_VALUE);
        UUID buyer = UUID.randomUUID(), seller = UUID.randomUUID();
        p.mint(buyer, 10_000, new TxnReason("t", "seed"));

        HoldResult h = p.hold(buyer, seller, 5_000, new TxnReason("market", "order-1"));
        eq(h.result(), TxnResult.OK, "托管成功");
        eq(p.balance(buyer), 5_000L, "托管当场扣款");
        eq(p.balance(seller), 0L, "受益人还没拿到");
        eq(esc.held("myserver:coin"), 5_000L, "钱押在托管里 —— 对账要把它算进总量");

        eq(p.release(h.id(), new TxnReason("market", "deliver-1")), TxnResult.OK, "放款");
        eq(p.balance(seller), 5_000L, "受益人收到");
        eq(esc.held("myserver:coin"), 0L, "托管里没有了");

        eq(p.release(h.id(), new TxnReason("market", "deliver-1")), TxnResult.ALREADY_SETTLED,
                "重复放款认得出来，不是 FAILED");
        eq(p.refund(h.id(), new TxnReason("market", "cancel")), TxnResult.ALREADY_SETTLED,
                "放过款就不能再退");
        eq(p.balance(seller), 5_000L, "重复结算没有把钱变多");

        // 退款那条路
        HoldResult h2 = p.hold(buyer, seller, 1_000, new TxnReason("market", "order-2"));
        long sellerBefore = p.balance(seller);
        eq(p.balance(buyer), 4_000L, "第二笔托管当场又扣了 1000");
        eq(p.refund(h2.id(), new TxnReason("market", "cancel-2")), TxnResult.OK, "退款");
        eq(p.balance(buyer), 5_000L, "钱退回原主");
        eq(p.balance(seller), sellerBefore, "受益人一分没多 —— 退款的方向是创建时定死的");

        eq(p.release(new EscrowId(UUID.randomUUID()), new TxnReason("t", "x")),
                TxnResult.UNKNOWN_ESCROW, "不认识的托管号");
    }

    /** §22.4：超时（默认 7 天）的托管会被扫出来。 */
    static void escrowTimeout() {
        AtomicLong t = new AtomicLong(0);
        EscrowLedger esc = new EscrowLedger(t::get);
        EscrowId id = esc.create(UUID.randomUUID(), UUID.randomUUID(), "myserver:coin", 100);
        eq(esc.expired().size(), 0, "刚建的不算超时");
        t.set(EscrowLedger.DEFAULT_TIMEOUT_MS - 1);
        eq(esc.expired().size(), 0, "差一毫秒还不算");
        t.set(EscrowLedger.DEFAULT_TIMEOUT_MS);
        eq(esc.expired().size(), 1, "到 7 天就该退了");
        esc.settle(id);
        eq(esc.expired().size(), 0, "结算过的不再算超时");
        eq(EscrowLedger.DEFAULT_TIMEOUT_MS, 7L * 24 * 3600 * 1000, "§22.4 默认 7 天");
    }

    /** §22.10：对账平；人为改余额之后能报出不平。 */
    static void audit() throws Exception {
        MemBalances bal = new MemBalances();
        AtomicLong t = new AtomicLong(0);
        EscrowLedger esc = new EscrowLedger(t::get);
        TxnLog log = new TxnLog(tmpDir("audit"), java.time.ZoneId.of("UTC"));
        BuiltinProvider p = provider(bal, esc, log, t, Long.MAX_VALUE);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();

        p.mint(a, 10_000, new TxnReason("t", "1"));
        p.transfer(a, b, 3_000, new TxnReason("t", "2"));
        p.burn(b, 1_000, new TxnReason("t", "3"));
        p.hold(a, b, 2_000, new TxnReason("t", "4"));

        EconomyAudit.Result r = EconomyAudit.run("myserver:coin", bal, esc, log);
        eq(r.minted(), 10_000L, "铸造总额");
        eq(r.burned(), 1_000L, "销毁总额");
        eq(r.expected(), 9_000L, "应有 = 铸造 − 销毁");
        eq(r.actual(), 9_000L, "实际 = 余额 + 托管");
        check(r.balanced(), "对账平：" + r.describe());

        // 人为改存档：给 a 凭空加 500
        bal.set(a, "myserver:coin", bal.get(a, "myserver:coin") + 500);
        EconomyAudit.Result bad = EconomyAudit.run("myserver:coin", bal, esc, log);
        check(!bad.balanced(), "改过存档之后必须报不平");
        eq(bad.delta(), 500L, "差额正好是被塞进去的那 500");
        check(bad.describe().contains("⚠"), "不平那一行要显眼，别埋在日志里");
    }

    /** §22.7：emc_legacy 的能力缺失是明写的，不是悄悄降级。 */
    static void emcLegacy() {
        LegacyWalletProvider p = new LegacyWalletProvider(coin());
        eq(p.hold(UUID.randomUUID(), UUID.randomUUID(), 100, new TxnReason("t", "1")).result(),
                TxnResult.UNAVAILABLE, "没有托管 —— 所以市场类 App 不能用它");
        eq(p.transfer(UUID.randomUUID(), UUID.randomUUID(), 100, new TxnReason("t", "2")),
                TxnResult.UNAVAILABLE, "旧接口没有存入这一侧，转账做不到");
        eq(p.mint(UUID.randomUUID(), 100, new TxnReason("t", "3")), TxnResult.UNAVAILABLE, "不能铸造");
        check(!p.unavailableReasonKey().isEmpty(), "要给得出理由的本地化键");
        check(!LegacyWalletProvider.supportsEscrow(p), "市场类 App 的安装判定据此把它标为不可用");

        MemBalances bal = new MemBalances();
        AtomicLong t = new AtomicLong(0);
        check(LegacyWalletProvider.supportsEscrow(
                        new BuiltinProvider(coin(), bal, new EscrowLedger(t::get), null, t::get, false, 100)),
                "builtin 支持托管");
    }

    /** §22.8：没有默认货币时 default() 返回 null，不抛。 */
    static void registry() {
        CurrencyRegistry reg = new CurrencyRegistry();
        eq(reg.defaultCurrency(), null, "什么都没配时 default() 是 null，不是抛异常");
        eq(reg.list().size(), 0, "list() 是空表");

        MemBalances bal = new MemBalances();
        AtomicLong t = new AtomicLong(0);
        reg.register(new BuiltinProvider(coin(), bal, new EscrowLedger(t::get), null, t::get, false, 100), false);
        eq(reg.defaultCurrency(), null, "注册了但没标默认，还是 null");
        eq(reg.list().size(), 1, "list() 有一个");

        reg.register(new LegacyWalletProvider(coin()), true);
        eq(reg.defaultCurrency(), "myserver:coin", "标了默认就有了");
        check(reg.get("myserver:coin") != null, "按 id 取得到");
        check(reg.get("nope:none") == null, "取不到的返回 null");
    }

    /** 勘误 E18：脚本侧金额是 BigInt，宿主桥在边界切 BigInt ↔ long。 */
    static void bigIntBridge() {
        BigInteger big = new BigInteger("9007199254740993");
        eq(Amounts.toLong(big, "test"), 9007199254740993L, "9007199254740993 过桥精度无损");
        eq(Amounts.toScript(9007199254740993L), big, "反向也无损");
        eq(Amounts.toWire(9007199254740993L), "9007199254740993", "线格式是十进制字符串");
        eq(Amounts.fromWire("9007199254740993"), 9007199254740993L, "从线格式读回来");

        eq(Amounts.toLong(BigInteger.valueOf(Long.MAX_VALUE), "t"), Long.MAX_VALUE, "long 的顶过得去");

        // 超出 long 的要当场拒，不能让 longValueExact 抛成一个内部错误
        checks++;
        try {
            Amounts.toLong(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), "t");
            failures.add("超出 long 的 BigInt 竟然收了");
        // ScriptAbort 是 extends Error 不是 RuntimeException（S13 实测：宿主抛
        // RuntimeException 时脚本一个 finally{return} 就能吞掉中断），所以这里要 catch Throwable
        } catch (Throwable e) {
            check(e.getMessage().contains("超出 long"), "报的是金额太大，实际 " + e.getMessage());
        }

        // 不是 BigInt 的一律拒：数字会丢精度，字符串是旧口径
        for (Object bad : new Object[]{Double.valueOf(1), Integer.valueOf(1), "123"}) {
            checks++;
            try {
                Amounts.toLong(bad, "t");
                failures.add(bad.getClass().getSimpleName() + " 竟然当金额收了");
            } catch (Throwable e) {
                check(e.getMessage().contains("BigInt"), "要提示写成 BigInt");
            }
        }
    }

    /** 流水：每一笔都进，失败的也进（§22.3 ⑥）。 */
    static void ledgerRecordsEverything() throws Exception {
        Path dir = tmpDir("log");
        AtomicLong t = new AtomicLong(1_700_000_000_000L);
        TxnLog log = new TxnLog(dir, java.time.ZoneId.of("UTC"));
        MemBalances bal = new MemBalances();
        BuiltinProvider p = provider(bal, new EscrowLedger(t::get), log, t, 1_000);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();

        p.mint(a, 500, new TxnReason("test", "ok"));
        p.transfer(a, b, 999_999, new TxnReason("test", "fail"));   // 余额不够，必失败

        Path f = dir.resolve("ledger").resolve("2023-11-14.log");
        check(Files.exists(f), "流水文件按日期建出来了：" + f);
        var lines = Files.readAllLines(f);
        eq(lines.size(), 2, "两笔都记了 —— 失败的也要进，那是查「谁在试探」的依据");
        check(lines.get(0).contains("|mint|"), "第一行是铸造");
        check(lines.get(0).endsWith("|OK"), "结果在最后一格");
        check(lines.get(1).contains("|transfer|"), "第二行是转账");
        check(lines.get(1).endsWith("|INSUFFICIENT"), "失败的结果也记下来了");

        eq(TxnLog.RETENTION_DAYS, 90, "§22.10 保留 90 天");
        eq(TxnLog.MAX_FILE_BYTES, 64L * 1024 * 1024, "§22.10 单日 64 MiB");
    }

    public static void main(String[] args) throws Exception {
        nonPositiveRejected();
        ceilingAndOverflow();
        concurrentConservation();
        escrow();
        escrowTimeout();
        audit();
        emcLegacy();
        registry();
        bigIntBridge();
        ledgerRecordsEverything();
        amountMustBePositive();
        ceilingDoesNotWrap();
        debits();
        formatParse();
        txnReason();
        holdResult();
        distinctCodes();
        conservation();

        System.out.println("断言 " + checks + " 条");
        if (!failures.isEmpty()) {
            System.out.println("失败 " + failures.size() + " 条：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }
}
