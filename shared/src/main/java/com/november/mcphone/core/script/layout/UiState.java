package com.november.mcphone.core.script.layout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一页的 UI 状态（施工方案 §9.7）：key 只有初值里声明过的那些，值是 int / bool / string / array / object，种类不变。不持久化。
 *
 * <p>{@link #revision()} 只在值真的变了时加一，页面拿它判断要不要重排（§7.6）。
 */
public final class UiState {

    private final Map<String, Object> values;
    private int revision;

    private UiState(Map<String, Object> values) {
        this.values = values;
    }

    /** 以初值建一份，例如 {@code NodeParser.Ui#state()}。 */
    public static UiState of(Map<String, Object> initial) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : initial.entrySet()) {
            Object v = e.getValue();
            if (StateRules.kind(v) == null) {
                throw new IllegalArgumentException("state['" + e.getKey() + "'] 只能是 int / bool / string / array / object，给的是 "
                        + (v == null ? "null" : v.getClass().getSimpleName()));
            }
            copy.put(Objects.requireNonNull(e.getKey(), "state 的 key"), StateRules.freeze(v));
        }
        return new UiState(copy);
    }

    /** 全部键值，只读。模板编译拿它知道声明了哪些 key、各是什么种类。 */
    public Map<String, Object> values() {
        return Collections.unmodifiableMap(values);
    }

    public static UiState empty() {
        return new UiState(new LinkedHashMap<>());
    }

    public int revision() {
        return revision;
    }

    /** 没有这个 key 时返回 null：P1 起数据可能来自服务端，缺键是常态（§9.5.5）。 */
    public Object get(String key) {
        return values.get(key);
    }

    public int getInt(String key) {
        return values.get(key) instanceof Integer i ? i : 0;
    }

    public boolean getBool(String key) {
        return values.get(key) instanceof Boolean b && b;
    }

    public String getString(String key) {
        return values.get(key) instanceof String s ? s : "";
    }

    /**
     * 改一个值。key 必须声明过、种类必须与初值相同，否则抛 {@link IllegalArgumentException}：
     * IR 校验已经保证了这两条，调用方绕过校验时在这里挡住，而不是把一个 String 塞进 int 的 key。
     * 按种类比而不是按 Class 比：两个内容相同的 List 可以是不同的实现类。
     */
    public void set(String key, Object value) {
        Object old = values.get(key);
        if (old == null) throw new IllegalArgumentException("state 里没有 '" + key + "'");
        String want = StateRules.kind(old);
        if (!want.equals(StateRules.kind(value))) {
            throw new IllegalArgumentException("state['" + key + "'] 是 " + want + "，不能写入 "
                    + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        if (!old.equals(value)) {
            values.put(key, StateRules.freeze(value));
            revision++;
        }
    }

    /** bool 取反（§4.5 的 toggle）。 */
    public void toggle(String key) {
        set(key, !getBool(key));
    }

    /** showIf / enabledIf 是否成立（§4.4）。truthy 为 true 时值为真才成立，为 false 时值为假才成立。 */
    public boolean test(Node.ShowIf condition) {
        Object v = values.get(condition.key());
        return switch (condition.kind()) {
            case EQ -> Objects.equals(v, condition.value());
            case NE -> !Objects.equals(v, condition.value());
            case TRUTHY -> truthy(v) == Boolean.TRUE.equals(condition.value());
        };
    }

    /** int 非 0、bool true、非空字符串、任何数组与对象（空的也算，§9.5.4）算真。 */
    static boolean truthy(Object v) {
        if (v instanceof Integer i) return i != 0;
        if (v instanceof Boolean b) return b;
        if (v instanceof List<?> || v instanceof Map<?, ?>) return true;
        return v instanceof String s && !s.isEmpty();
    }
}
