package com.november.mcphone.core.script.layout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一页的 UI 状态（施工方案 §4.2.1）：key 只有初值里声明过的那些，值只有 int / bool / string，类型不变。不持久化。
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
            if (!(v instanceof Integer || v instanceof Boolean || v instanceof String)) {
                throw new IllegalArgumentException("state['" + e.getKey() + "'] 只能是 int / bool / string，给的是 "
                        + (v == null ? "null" : v.getClass().getSimpleName()));
            }
            copy.put(Objects.requireNonNull(e.getKey(), "state 的 key"), v);
        }
        return new UiState(copy);
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
     * 改一个值。key 必须声明过、类型必须与初值相同，否则抛 {@link IllegalArgumentException}：
     * IR 校验已经保证了这两条，调用方绕过校验时在这里挡住，而不是把一个 String 塞进 int 的 key。
     */
    public void set(String key, Object value) {
        Object old = values.get(key);
        if (old == null) throw new IllegalArgumentException("state 里没有 '" + key + "'");
        if (value == null || value.getClass() != old.getClass()) {
            throw new IllegalArgumentException("state['" + key + "'] 是 " + old.getClass().getSimpleName()
                    + "，不能写入 " + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        if (!old.equals(value)) {
            values.put(key, value);
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

    /** int 非 0、bool true、非空字符串算真。 */
    static boolean truthy(Object v) {
        if (v instanceof Integer i) return i != 0;
        if (v instanceof Boolean b) return b;
        return v instanceof String s && !s.isEmpty();
    }
}
