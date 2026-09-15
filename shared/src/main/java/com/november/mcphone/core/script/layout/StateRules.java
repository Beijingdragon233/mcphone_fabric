package com.november.mcphone.core.script.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * state 的键名与初值规则（施工方案 §9.7）。IR 的 JSON 形态与 .vue 的 {@code <script>} 都过这一份：抄两份必然分叉。
 *
 * <p>值的 Java 形态只有 Integer / Boolean / String / List / Map。
 */
public final class StateRules {

    public static final int MAX_KEYS = 16;
    public static final int MAX_STRING = 64;
    public static final int MAX_ARRAY = 32;
    public static final int MAX_OBJECT_KEYS = 16;
    /** 数组套对象是 2 层，再往里就拒。 */
    public static final int MAX_DEPTH = 2;

    public static final Pattern KEY = Pattern.compile("[a-z][a-zA-Z0-9_]{0,31}");
    public static final String KEY_RULE = "匹配 [a-z][a-zA-Z0-9_]{0,31}";

    private StateRules() {
    }

    /** 值的种类：int / bool / string / array / object，别的返回 null。报错文案与类型比较都用它。 */
    public static String kind(Object value) {
        if (value instanceof Integer) return "int";
        if (value instanceof Boolean) return "bool";
        if (value instanceof String) return "string";
        if (value instanceof List<?>) return "array";
        if (value instanceof Map<?, ?>) return "object";
        return null;
    }

    /** 初值不合规时返回理由，合规返回 null。 */
    public static String check(Object value) {
        return check(value, 0);
    }

    private static String check(Object value, int depth) {
        String kind = kind(value);
        if (kind == null) return "只能是 int / bool / string / array / object，给的是 " + (value == null ? "null" : "别的");
        switch (kind) {
            case "string" -> {
                int len = ((String) value).length();
                return len > MAX_STRING ? "字符串 " + len + " 字，最多 " + MAX_STRING + " 字" : null;
            }
            case "array" -> {
                if (depth >= MAX_DEPTH) return "嵌套超过 " + MAX_DEPTH + " 层";
                List<?> list = (List<?>) value;
                if (list.size() > MAX_ARRAY) return "数组 " + list.size() + " 项，最多 " + MAX_ARRAY + " 项";
                Object first = list.isEmpty() ? null : list.get(0);
                for (Object e : list) {
                    String why = check(e, depth + 1);
                    if (why != null) return why;
                    // 同构：模板里 v-for 出来的每一项要能用同一套写法读，对象要同一组键
                    if (!sameShape(first, e)) return "数组元素要同一种形状，混了 " + shape(first) + " 和 " + shape(e);
                }
                return null;
            }
            case "object" -> {
                if (depth >= MAX_DEPTH) return "嵌套超过 " + MAX_DEPTH + " 层";
                Map<?, ?> map = (Map<?, ?>) value;
                if (map.size() > MAX_OBJECT_KEYS) {
                    return "对象 " + map.size() + " 个键，最多 " + MAX_OBJECT_KEYS + " 个";
                }
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (!(e.getKey() instanceof String k) || !KEY.matcher(k).matches()) {
                        return "对象的键 '" + e.getKey() + "' 要" + KEY_RULE;
                    }
                    Object v = e.getValue();
                    if (v instanceof List<?> || v instanceof Map<?, ?>) return "对象只能一层，'" + k + "' 的值不能再是 array / object";
                    String why = check(v, depth + 1);
                    if (why != null) return why;
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    /** 同种类；对象的键与各键的种类相同；数组套数组时比两边的首项（各自内部已经同构）。空数组与任何数组同形。 */
    private static boolean sameShape(Object a, Object b) {
        if (!java.util.Objects.equals(kind(a), kind(b))) return false;
        if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
            if (!ma.keySet().equals(mb.keySet())) return false;
            for (Object k : ma.keySet()) {
                if (!java.util.Objects.equals(kind(ma.get(k)), kind(mb.get(k)))) return false;
            }
            return true;
        }
        if (a instanceof List<?> la && b instanceof List<?> lb) {
            return la.isEmpty() || lb.isEmpty() || sameShape(la.get(0), lb.get(0));
        }
        return true;
    }

    private static String shape(Object v) {
        if (v instanceof Map<?, ?> m) return "object" + new java.util.TreeSet<>(m.keySet().stream().map(String::valueOf).toList());
        return kind(v);
    }

    /** 只读的深拷贝。state 里的数组交给模板读，被外面改了会让两次重排之间悄悄变样。 */
    public static Object freeze(Object value) {
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object e : list) out.add(freeze(e));
            return Collections.unmodifiableList(out);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) out.put(String.valueOf(e.getKey()), freeze(e.getValue()));
            return Collections.unmodifiableMap(out);
        }
        return value;
    }
}
