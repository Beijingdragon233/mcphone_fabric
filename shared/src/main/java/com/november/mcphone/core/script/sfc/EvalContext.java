package com.november.mcphone.core.script.sfc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一次重排（或一次点击）的求值环境：state、v-for 的循环变量、§9.5.7 的运行期上限与 warn。
 *
 * <p>超限一律降级不抛：表达式抛异常会让整页挂掉，而数据可能来自服务端，长度与缺键在运行期都不可控。
 */
final class EvalContext {

    static final int MAX_DEPTH = 32;
    static final int MAX_EVALS = 4096;
    static final int MAX_CONCAT = 1024;
    /** 同一次重排的 warn 去重后最多留这么多条，每帧重排时日志不至于刷屏。 */
    static final int MAX_WARNINGS = 32;

    private final Map<String, Object> state;
    private final String file;
    private final List<String> names = new ArrayList<>();
    private final List<Object> values = new ArrayList<>();
    private final Set<String> warnings = new LinkedHashSet<>();
    private int evals;
    private boolean exhausted;

    int depth;
    int line;

    EvalContext(Map<String, Object> state, String file) {
        this.state = state;
        this.file = file;
    }

    void push(String name, Object value) {
        names.add(name);
        values.add(value);
    }

    void pop() {
        names.remove(names.size() - 1);
        values.remove(values.size() - 1);
    }

    /** 此刻绑着的 v-for 变量名，外层在前。@click 实例化时拿它捕获，点击时还原。 */
    List<String> boundNames() {
        return List.copyOf(names);
    }

    /** 与 {@link #boundNames()} 一一对应的值。可能有 null（数组里写了 null），所以不用 List.copyOf。 */
    List<Object> boundValues() {
        return java.util.Collections.unmodifiableList(new ArrayList<>(values));
    }

    /** 循环变量先于 state：编译期已经拒了遮蔽，这里的顺序只对嵌套 v-for 的同名变量有意义。 */
    Object lookup(String name) {
        for (int i = names.size() - 1; i >= 0; i--) {
            if (names.get(i).equals(name)) return values.get(i);
        }
        return state.get(name);
    }

    /** 记一次求值。用完 4096 次后返回 false，只在第一次超出时记 warn。 */
    boolean budget() {
        if (evals >= MAX_EVALS) {
            if (!exhausted) {
                exhausted = true;
                warnAlways("一次重排求值超过 " + MAX_EVALS + " 次，剩下的表达式按 null 处理");
            }
            return false;
        }
        evals++;
        return true;
    }

    boolean exhausted() {
        return exhausted;
    }

    void warn(String reason) {
        if (warnings.size() < MAX_WARNINGS) warnings.add(file + ":" + line + " " + reason);
    }

    /** 截断、求值预算用完这类「页面少了一截」的 warn 不受条数上限挡：前面刷满 32 条后它们会悄悄消失。 */
    void warnAlways(String reason) {
        warnings.add(file + ":" + line + " " + reason);
    }

    List<String> warnings() {
        return List.copyOf(warnings);
    }

    /** 拼接结果超长时截断并记 warn。 */
    String capped(String s) {
        if (s.length() <= MAX_CONCAT) return s;
        warn("拼出来的字符串 " + s.length() + " 字，截到 " + MAX_CONCAT);
        return Values.cut(s, MAX_CONCAT);
    }
}
