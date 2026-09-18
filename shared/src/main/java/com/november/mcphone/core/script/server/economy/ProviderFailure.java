package com.november.mcphone.core.script.server.economy;

/**
 * provider（第三方代码）抛来的异常的替身：只带原来的类名与堆栈。
 *
 * <p>原来那个不交给日志、也不挂成 cause：它的 getMessage / getStackTrace 可能自己会炸，log4j 渲染时一炸整条日志就没了，
 * 原版日志配置下还会把异常抛回调用方（脚本求值线程就这样死掉、请求永远没有回复）。
 */
public final class ProviderFailure extends RuntimeException {

    private ProviderFailure(String className) {
        super(className);
    }

    /** 造一个替身。不抛：原来那个的 getStackTrace 炸了就不带堆栈。 */
    public static ProviderFailure of(Throwable original) {
        ProviderFailure s = new ProviderFailure(original.getClass().getName());
        try {
            s.setStackTrace(original.getStackTrace());
        } catch (Throwable ignored) {
            s.setStackTrace(new StackTraceElement[0]);
        }
        return s;
    }
}
