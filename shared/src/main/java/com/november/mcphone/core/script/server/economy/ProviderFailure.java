package com.november.mcphone.core.script.server.economy;

/**
 * provider（第三方代码）抛来的异常的替身：带原来的类名、截短的 message、堆栈，不带 cause。
 *
 * <p>原来那个不交给日志、也不挂成 cause：它的 getMessage / getStackTrace / getCause 都可能自己会炸，log4j 渲染时一炸整条日志就没了，
 * 原版日志配置下还会把异常抛回调用方（脚本求值线程就这样死掉、请求永远没有回复）。message 在这里取一次、接住、截短，
 * 渲染时不再碰原来那个。
 */
public final class ProviderFailure extends RuntimeException {

    private ProviderFailure(String className) {
        super(className);
    }

    /** message 最多留多少字符。 */
    private static final int MAX_MESSAGE = 500;

    /** 造一个替身；已经是替身就原样返回。不抛：原来那个的 getMessage / getStackTrace 炸了就不带那一样。 */
    public static ProviderFailure of(Throwable original) {
        if (original instanceof ProviderFailure pf) return pf;
        ProviderFailure s = new ProviderFailure(original.getClass().getName() + safeMessage(original));
        try {
            s.setStackTrace(original.getStackTrace());
        } catch (Throwable ignored) {
            s.setStackTrace(new StackTraceElement[0]);
        }
        return s;
    }

    // 缺失的方法签名、NPE 的说明、provider 自己的原因键都在 message 里：值得留，但只在这里取一次
    private static String safeMessage(Throwable t) {
        try {
            String m = t.getMessage();
            if (m == null) return "";
            return ": " + (m.length() > MAX_MESSAGE ? m.substring(0, MAX_MESSAGE) + "…" : m);
        } catch (Throwable e) {
            return "（getMessage 抛了 " + e.getClass().getName() + "）";
        }
    }
}
