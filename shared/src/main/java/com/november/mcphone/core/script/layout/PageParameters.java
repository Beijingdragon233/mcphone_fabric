package com.november.mcphone.core.script.layout;

import com.google.common.collect.ImmutableMap;

import java.util.Map.Entry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small immutable navigation values, independent of page state and the script engine. */
public final class PageParameters {
    private final Map<String, Object> values;

    public Map<String, Object> values() { return values; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PageParameters)) return false;
        PageParameters that = (PageParameters) other;
        return java.util.Objects.equals(this.values, that.values);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(values);
        return hash;
    }

    @Override public String toString() { return "PageParameters[values=" + values + "]"; }

    public static final int MAX_KEYS = 4;
    public static final int MAX_STRING = 256;
    public static final PageParameters EMPTY = new PageParameters(ImmutableMap.of());

    public PageParameters(Map<String, Object> values) {
        if (values == null || values.size() > MAX_KEYS) throw new IllegalArgumentException("导航参数最多 4 个字段");
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Entry<String,Object> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key == null || !StateRules.KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("导航参数字段名要" + StateRules.KEY_RULE);
            }
            Object value = entry.getValue();
            if (!(value instanceof String || value instanceof Integer || value instanceof Boolean)) {
                throw new IllegalArgumentException("导航参数只能是字符串、32 位整数或布尔值");
            }
            if (value instanceof String) {
                if (((String) value).length() > MAX_STRING) throw new IllegalArgumentException("导航参数字符串最多 256 个 UTF-16 单元");
                for (int i = 0; i < ((String) value).length(); i++) {
                    char c = ((String) value).charAt(i);
                    if (Character.isHighSurrogate(c)) {
                        if (++i < ((String) value).length() && Character.isLowSurrogate(((String) value).charAt(i))) continue;
                        throw new IllegalArgumentException("导航参数包含不完整的 Unicode 字符");
                    }
                    if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("导航参数包含不完整的 Unicode 字符");
                }
            }
            copy.put(key, value);
        }
        values = Collections.unmodifiableMap(copy);
        this.values = values;
    }

    public static PageParameters from(Object value) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("nav 的第二个参数必须是普通对象");
        if (((Map<?, ?>) value).size() > MAX_KEYS) throw new IllegalArgumentException("导航参数最多 4 个字段");
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Entry<?,?> entry : ((Map<?, ?>) value).entrySet()) {
            Object keyMatch;
            if (!((keyMatch = entry.getKey()) instanceof String)) throw new IllegalArgumentException("导航参数字段名必须是字符串");
            fields.put(((String) keyMatch), entry.getValue());
        }
        return fields.isEmpty() ? EMPTY : new PageParameters(fields);
    }
}
