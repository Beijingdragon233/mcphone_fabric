package com.november.mcphone.api.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public static void main(String[] args) {
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
