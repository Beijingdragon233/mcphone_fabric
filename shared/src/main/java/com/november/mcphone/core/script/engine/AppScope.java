package com.november.mcphone.core.script.engine;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.Map;

/**
 * 一个 App 的后端 scope（施工方案 §16.3"每个 App 一个独立 scope"、§15.2 的 {@code actions} 表）。
 *
 * <h2>为什么每次调用还要再建一个子 scope</h2>
 *
 * {@code actions} 表必须跨调用活着，所以 App 的 scope 是复用的。而 §16.4 的两道预算都是
 * <b>每次调用</b>的，于是跨调用累积整个绕过了预算。实测：脚本 {@code g = g + g;}，
 * 每次调用只有个位数条指令、一次都没超预算，<b>第 26 次调用 OutOfMemoryError</b>。
 *
 * <p>两条一起堵：
 * <ol>
 *   <li>每次调用在一个<b>子 scope</b> 上跑，{@code var} 声明落在子 scope，调用结束即丢</li>
 *   <li>调用结束扫一眼 App scope 顶层的驻留量，超 {@link #MAX_RETAINED_CHARS} 就重建 scope 并记审计</li>
 * </ol>
 *
 * <p>扫驻留量是便宜的：{@code CharSequence.length()} 对 {@code ConsString} 是 O(1)，
 * 不会因为量尺寸把 rope 物化。建一次 App scope 实测 0.76 毫秒。
 */
public final class AppScope {

    /** 一个 App 的 scope 顶层最多留多少字符。超了就重建。 */
    public static final long MAX_RETAINED_CHARS = 1 << 20;

    /** 扫驻留量时最多看几个属性，防着一个有几万个键的 scope 把扫描本身变成负担。 */
    public static final int MAX_SCANNED = 4096;

    private final String appId;
    private final ScriptBudget budget;
    private final ScriptModules modules;

    private ScriptableObject scope;

    public AppScope(String appId, ScriptBudget budget, Map<String, String> jsSources) {
        this.appId = appId;
        this.budget = budget;
        this.modules = new ScriptModules(jsSources);
    }

    public String appId() {
        return appId;
    }

    public ScriptModules modules() {
        return modules;
    }

    /** 这个 App 的顶层 scope。第一次用时建。<b>只在持有 Context 的线程上调。</b> */
    public ScriptableObject scope(Context cx) {
        if (scope == null) scope = ScriptSandbox.harden(cx);
        return scope;
    }

    /**
     * 一次调用用的子 scope。
     *
     * <p>{@code setParentScope(null)}：顶层赋值会打到已经密封的 App scope 上并报错，
     * 而不是<b>悄悄</b>驻留一份。
     */
    public Scriptable callScope(Context cx) {
        ScriptableObject parent = scope(cx);
        Scriptable call = cx.newObject(parent);
        call.setPrototype(parent);
        call.setParentScope(null);
        return call;
    }

    /** {@code actions} 表。没有就返回 null。 */
    public Scriptable actions(Context cx) {
        Object a = ScriptableObject.getProperty(scope(cx), "actions");
        return a instanceof Scriptable s ? s : null;
    }

    /**
     * 调用结束扫一遍驻留量。超限就把 scope 丢掉重建，下次调用重新装载。
     *
     * @return 超没超。超了调用方要记一条审计
     */
    public boolean sweepRetained() {
        if (scope == null) return false;
        long chars = 0;
        int seen = 0;
        for (Object id : scope.getIds()) {          // 只看脚本自己声明的（可枚举的那些）
            if (++seen > MAX_SCANNED) break;
            if (!(id instanceof String name)) continue;
            Object v = ScriptableObject.getProperty(scope, name);
            if (v instanceof CharSequence cs) chars += cs.length();   // ConsString 上是 O(1)
        }
        if (chars <= MAX_RETAINED_CHARS) return false;
        scope = null;                                // 下次 scope(cx) 会重建
        return true;
    }

    /** 服务器停止或 App 卸载时叫。 */
    public void discard() {
        scope = null;
    }

    public ScriptBudget budget() {
        return budget;
    }
}
