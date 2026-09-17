package com.november.mcphone.api.economy;

/**
 * §22.9 那张不变量表，写成代码。<b>纯算术，各实现共用这一份。</b>
 *
 * <p>各写各的话，"金额必须为正"这种一行的检查总有一个实现会漏掉，
 * 而漏掉的后果是 {@code transfer(A, B, -1000)} 从对方账上偷 1000 —— 最经典的那个经济漏洞。
 * 摆在这里，{@code ICurrencyProvider} 的实现只要照着调，就不会各漏各的。
 */
public final class Balances {

    private Balances() {
    }

    /**
     * 金额本身合不合法。<b>{@code amount <= 0} 一律拒</b>（§22.9）：
     * 负数转账等于反向偷钱，0 元转账只是往流水里灌垃圾。
     */
    public static TxnResult checkAmount(long amount) {
        return amount <= 0 ? TxnResult.INVALID : TxnResult.OK;
    }

    /**
     * 往 {@code current} 上加 {@code amount} 行不行。
     *
     * <p>超上限与加法溢出都返回 {@link TxnResult#LIMIT}，<b>不回绕</b>：
     * 回绕会让一个余额爆表的账户在收一笔钱之后变成负数。
     */
    public static TxnResult checkCredit(long current, long amount, long maxBalance) {
        TxnResult bad = checkAmount(amount);
        if (bad != TxnResult.OK) return bad;
        long after;
        try {
            after = Math.addExact(current, amount);
        } catch (ArithmeticException e) {
            return TxnResult.LIMIT;
        }
        return after > maxBalance ? TxnResult.LIMIT : TxnResult.OK;
    }

    /** 从 {@code current} 上扣 {@code amount} 行不行。不够就 {@link TxnResult#INSUFFICIENT}。 */
    public static TxnResult checkDebit(long current, long amount, boolean allowNegative) {
        TxnResult bad = checkAmount(amount);
        if (bad != TxnResult.OK) return bad;
        if (allowNegative) {
            try {
                Math.subtractExact(current, amount);
            } catch (ArithmeticException e) {
                return TxnResult.LIMIT;   // 允许负数也不许绕到正数去
            }
            return TxnResult.OK;
        }
        return current < amount ? TxnResult.INSUFFICIENT : TxnResult.OK;
    }

    /**
     * 最小单位的整数 → 显示用的字符串，如 {@code 1234} + {@code decimals=2} → {@code "12.34"}。
     *
     * <p><b>不许自己拼小数点</b>（§22.5）：各 App 自己拼，负数与不足位就各错各的。
     */
    public static String format(long amount, int decimals) {
        if (decimals <= 0) return Long.toString(amount);
        boolean neg = amount < 0;
        // 先取绝对值再补零：-5 在 decimals=2 下是 "-0.05"，按负数直接除会得到 "-0.-5"
        String digits = Long.toString(Math.abs(amount));
        while (digits.length() <= decimals) digits = "0" + digits;
        int cut = digits.length() - decimals;
        return (neg ? "-" : "") + digits.substring(0, cut) + "." + digits.substring(cut);
    }

    /**
     * 显示用的字符串 → 最小单位的整数。解析不了抛 {@link NumberFormatException}。
     *
     * <p>小数位多于 {@code decimals} 就抛，不四舍五入：悄悄抹掉一位就是悄悄改了金额。
     */
    public static long parse(String text, int decimals) {
        if (text == null) throw new NumberFormatException("金额为 null");
        String s = text.trim();
        if (s.isEmpty()) throw new NumberFormatException("金额为空");
        boolean neg = s.startsWith("-");
        if (neg || s.startsWith("+")) s = s.substring(1);
        int dot = s.indexOf('.');
        String whole = dot < 0 ? s : s.substring(0, dot);
        String frac = dot < 0 ? "" : s.substring(dot + 1);
        if (frac.length() > decimals) {
            throw new NumberFormatException("小数位最多 " + decimals + " 位，收到 '" + text + "'");
        }
        if (whole.isEmpty()) whole = "0";
        if (!whole.chars().allMatch(Character::isDigit) || !frac.chars().allMatch(Character::isDigit)) {
            throw new NumberFormatException("不是十进制金额：'" + text + "'");
        }
        StringBuilder sb = new StringBuilder(whole).append(frac);
        for (int i = frac.length(); i < decimals; i++) sb.append('0');
        long v = Long.parseLong(sb.toString());
        return neg ? -v : v;
    }
}
