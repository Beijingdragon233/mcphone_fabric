package com.november.mcphone.core.script.engine;

import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;

/**
 * 边界尺寸闸（施工方案 §16.4 的"必须做的三件事 ①"）。
 *
 * <h2>为什么不能指望指令预算</h2>
 *
 * 指令预算与墙钟<b>只在 Rhino 的分支点生效</b>，单个原生操作里观察器一次都不触发。实测：
 *
 * <pre>
 * new ArrayBuffer(2e8)      19 ms 分配 200 MB，观察器回调 0 次
 * new Int8Array(2e8)        94 ms                回调 0 次
 * 'x'.repeat(1e8)          158 ms                回调 0 次
 * JSON.stringify(16M 串)   419 ms                回调 0 次
 * s+=s 翻倍 30 次再 .length  4 ms 得到 1073741824  回调 0 次
 * </pre>
 *
 * 所以尺寸要在<b>进出宿主与内置方法的每个边界</b>上量，而不是等预算。
 *
 * <h2>量长度不会把 rope 物化</h2>
 *
 * Rhino 的字符串拼接是惰性的（{@code ConsString}），{@code s+=s} 不真复制；
 * 一旦被 {@code charAt} / {@code indexOf} 强制物化，9 毫秒就能打爆 256 MB 堆。
 * 而 {@code CharSequence.length()} 对 {@code ConsString} 是 O(1)，<b>量它是安全的</b>。
 */
public final class SizeGate {

    private SizeGate() {
    }

    /** 字符串上限，§16.4 ①。 */
    public static final int MAX_STRING = 64 * 1024;

    /** 数组上限，§16.4 ①。 */
    public static final int MAX_ARRAY = 4096;

    /** 一个值过不过得了闸。过不了就中断本次调用。 */
    public static void check(Object value, String where) {
        if (value instanceof CharSequence cs) {
            // length() 对 ConsString 是 O(1)，不会因为量尺寸把它物化
            if (cs.length() > MAX_STRING) {
                throw new ScriptAbort(ScriptAbort.Reason.SIZE,
                        where + ": 字符串 " + cs.length() + " 字符，上限 " + MAX_STRING);
            }
            return;
        }
        if (value instanceof NativeArray arr) {
            long n = arr.getLength();
            if (n > MAX_ARRAY) {
                throw new ScriptAbort(ScriptAbort.Reason.SIZE,
                        where + ": 数组 " + n + " 个元素，上限 " + MAX_ARRAY);
            }
            return;
        }
        if (value instanceof Scriptable s && !(value instanceof NativeArray)) {
            Object len = s.get("length", s);
            if (len instanceof Number n && n.longValue() > MAX_ARRAY) {
                throw new ScriptAbort(ScriptAbort.Reason.SIZE,
                        where + ": 类数组 " + n.longValue() + " 个元素，上限 " + MAX_ARRAY);
            }
        }
    }

    /** 一批值（参数表）。 */
    public static void checkAll(Object[] values, String where) {
        if (values == null) return;
        for (Object v : values) check(v, where);
    }
}
