package com.november.mcphone.core.script.sfc;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.november.mcphone.core.script.layout.NodeParser;
import com.november.mcphone.core.script.layout.NodeType;
import com.november.mcphone.core.script.layout.StateRules;
import com.november.mcphone.core.script.pkg.PackageError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 模板属性 → Node props（施工方案 §5.4 的专有字段、§9.4.2 的静态与绑定）。
 *
 * <p>普通控件沿用 {@link NodeParser} 的逐字段判据，SFC text 正文支持独立的长文上限。静态值编译期不合规就拒；
 * 绑定值在实例化时才知道，不合规时夹紧或丢掉并记 warn，不抛：数据可能来自服务端。
 */
final class PropRules {

    enum Kind { INT, BOOL, TEXT, STRING, ICON, IMAGE_SRC, BIND_INT, BIND_BOOL, BIND_STRING, TABS, ARGS }

    /** 一个属性。staticOnly 的只能写字面值（bind 是 key 名），boundOnly 的只能写 :attr（数组）。 */
    static final class Spec {
        private final String name;
        private final Kind kind;
        private final int min;
        private final int max;

        Spec(String name, Kind kind, int min, int max) {
            this.name = name;
            this.kind = kind;
            this.min = min;
            this.max = max;
        }

        public String name() { return name; }

        public Kind kind() { return kind; }

        public int min() { return min; }

        public int max() { return max; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Spec)) return false;
            Spec that = (Spec) other;
            return java.util.Objects.equals(this.name, that.name)
                    && java.util.Objects.equals(this.kind, that.kind)
                    && this.min == that.min
                    && this.max == that.max;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(name);
            hash = 31 * hash + java.util.Objects.hashCode(kind);
            hash = 31 * hash + java.util.Objects.hashCode(min);
            hash = 31 * hash + java.util.Objects.hashCode(max);
            return hash;
        }

        @Override public String toString() { return "Spec[name=" + name + ", kind=" + kind + ", min=" + min + ", max=" + max + "]"; }

        boolean staticOnly() {
            return kind == Kind.BIND_INT || kind == Kind.BIND_BOOL || kind == Kind.BIND_STRING;
        }

        boolean boundOnly() {
            return kind == Kind.TABS || kind == Kind.ARGS;
        }

        /** 接受数字或布尔的属性：静态值恰好是 state 的 key 名时多半是忘了冒号（§9.4.2）。 */
        boolean numberOrBool() {
            return kind == Kind.INT || kind == Kind.BOOL;
        }

        String allowed() {
            switch (kind) {
                case INT:
                    return max == Integer.MAX_VALUE ? "≥ " + min + " 的整数" : min + "–" + max + " 的整数";
                case BOOL:
                    return "true / false";
                case TEXT:
                    return "最多 " + textLimit(this) + " 字，除换行外没有控制字符";
                case STRING:
                    return "字符串";
                case ICON:
                    return String.join(" ", Names.sorted(NodeParser.ICON_NAMES));
                case IMAGE_SRC:
                    return "包内相对路径，以 .png 结尾";
                case BIND_INT:
                    return "state 里一个 int 的 key 名";
                case BIND_BOOL:
                    return "state 里一个 bool 的 key 名";
                case BIND_STRING:
                    return "state 里一个 string 的 key 名";
                case TABS:
                    return ":tabs=\"[{text:'…'}, …]\"，2–4 项，每项恰好一个 text / i18n / icon";
                case ARGS:
                    return ":args=\"[…]\"，最多 " + NodeParser.MAX_ARGS + " 个 string 或 int";
                default: throw new IncompatibleClassChangeError();
            }
        }
    }

    /** 值不合规。allowed 是报给作者看的允许取值。 */
    static final class Bad extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Bad(String allowed) {
            super(allowed, null, false, false);
        }
    }

    private static final Pattern PLAIN_INT = Pattern.compile("-?\\d{1,10}");
    private static final Set<String> TAB_FIELDS = ImmutableSet.of("text", "i18n", "icon");

    private PropRules() {
    }

    /** 这个类型在模板里接受的专有属性，按 §5.4 的顺序。 */
    static Map<String, Spec> of(NodeType type) {
        Map<String, Spec> out = new LinkedHashMap<>();
        switch (type) {
            case GRID:
                put(out, "cols", Kind.INT, 1, 6);
                break;
            case LIST:
                put(out, "item-height", Kind.INT, 0, Integer.MAX_VALUE);
                break;
            case SPACER:
                put(out, "size", Kind.INT, 0, Integer.MAX_VALUE);
                break;
            case DIVIDER:
                put(out, "vertical", Kind.BOOL, 0, 0);
                break;
            case TEXT: {
                put(out, "text", Kind.TEXT, 0, StateRules.MAX_PAGE_STRING);
                put(out, "i18n", Kind.STRING, 0, 0);
                put(out, "args", Kind.ARGS, 0, 0);
            }
                break;
            case IMAGE: {
                put(out, "src", Kind.IMAGE_SRC, 0, 0);
                put(out, "w", Kind.INT, 1, 120);
                put(out, "h", Kind.INT, 1, 176);
            }
                break;
            case ICON: {
                put(out, "name", Kind.ICON, 0, 0);
                put(out, "size", Kind.INT, 6, 20);
            }
                break;
            case ITEM: {
                put(out, "item", Kind.STRING, 0, 0);
                put(out, "count", Kind.INT, 1, 99);
                put(out, "size", Kind.INT, 8, 20);
            }
                break;
            case BADGE:
                put(out, "count", Kind.INT, 0, Integer.MAX_VALUE);
                break;
            case PROGRESS: {
                put(out, "value", Kind.INT, 0, 100);
                put(out, "height", Kind.INT, 2, 12);
            }
                break;
            case BUTTON: {
                put(out, "text", Kind.TEXT, 0, 0);
                put(out, "i18n", Kind.STRING, 0, 0);
                put(out, "args", Kind.ARGS, 0, 0);
                put(out, "enabled", Kind.BOOL, 0, 0);
            }
                break;
            case TEXT_INPUT:
            case TEXTAREA: {
                put(out, "bind", Kind.BIND_STRING, 0, 0);
                put(out, "placeholder", Kind.TEXT, 0, 0);
                put(out, "max-length", Kind.INT, 1,
                        type == NodeType.TEXTAREA ? StateRules.MAX_PAGE_STRING : StateRules.MAX_STRING);
                put(out, "enabled", Kind.BOOL, 0, 0);
            }
                break;
            case TOGGLE: {
                put(out, "bind", Kind.BIND_BOOL, 0, 0);
                put(out, "label", Kind.TEXT, 0, 0);
                put(out, "i18n", Kind.STRING, 0, 0);
                put(out, "enabled", Kind.BOOL, 0, 0);
            }
                break;
            case TAB_BAR: {
                put(out, "bind", Kind.BIND_INT, 0, 0);
                put(out, "tabs", Kind.TABS, 0, 0);
            }
                break;
            default: {
                // box / column / row / stack / scroll 没有专有属性
            }
                break;
        }
        return out;
    }

    private static void put(Map<String, Spec> out, String name, Kind kind, int min, int max) {
        out.put(name, new Spec(name, kind, min, max));
    }

    /** 必填的属性组：组里至少写一个。text 的内容、button 的子元素算不算写了由编译器判。 */
    static List<List<String>> required(NodeType type) {
        switch (type) {
            case TEXT:
            case BUTTON:
                return ImmutableList.of(ImmutableList.of("text", "i18n"));
            case IMAGE:
                return ImmutableList.of(ImmutableList.of("src"));
            case ICON:
                return ImmutableList.of(ImmutableList.of("name"));
            case ITEM:
                return ImmutableList.of(ImmutableList.of("item"));
            case TOGGLE:
            case TEXT_INPUT:
            case TEXTAREA:
                return ImmutableList.of(ImmutableList.of("bind"));
            case TAB_BAR:
                return ImmutableList.of(ImmutableList.of("bind"), ImmutableList.of("tabs"));
            default:
                return ImmutableList.of();
        }
    }

    // ============================================================
    //  静态值：编译期
    // ============================================================

    /** 写成 attr="raw" 的值。raw 为 null 表示只写了属性名。bind 的 key 是否存在由编译器对着 state 判。 */
    static Object parseStatic(Spec spec, String raw) {
        if (raw == null) {
            if (spec.kind == Kind.BOOL) return true;
            throw new Bad(spec.allowed());
        }
        switch (spec.kind) {
            case INT: {
                if (!PLAIN_INT.matcher(raw).matches()) throw new Bad(spec.allowed());
                long v = Long.parseLong(raw);
                if (v < spec.min || v > spec.max) throw new Bad(spec.allowed());
                return (int) v;
            }
            case BOOL: {
                if (raw.equals("true")) return true;
                if (raw.equals("false")) return false;
                throw new Bad(spec.allowed());
            }
            case TEXT: {
                if (raw.length() > textLimit(spec) || !visible(raw)) throw new Bad(spec.allowed());
                return raw;
            }
            case ICON: {
                if (!NodeParser.ICON_NAMES.contains(raw)) throw new Bad(spec.allowed());
                return raw;
            }
            case IMAGE_SRC: {
                if (!imageSrc(raw)) throw new Bad(spec.allowed());
                return raw;
            }
            case TABS:
            case ARGS:
                throw new Bad(spec.allowed());
            default: {
                return raw;
            }
        }
    }

    // ============================================================
    //  绑定值：实例化时
    // ============================================================

    /** 绑定求出的值 → 放进 props 的值；返回 null 表示丢掉这个属性（已记 warn）。 */
    static Object normalize(Spec spec, Object v, EvalContext c, String tag) {
        String where = "<" + tag + "> 的 :" + spec.name;
        switch (spec.kind) {
            case INT: {
                if (!(v instanceof Integer)) return drop(c, where, v, spec);
                if (((Integer) v) < spec.min || ((Integer) v) > spec.max) {
                    int clamped = Math.max(spec.min, Math.min(spec.max, ((Integer) v)));
                    c.warn(where + " 得到 " + ((Integer) v) + "，夹到 " + clamped);
                    return clamped;
                }
                return ((Integer) v);
            }
            case BOOL: {
                return v instanceof Boolean ? v : drop(c, where, v, spec);
            }
            case TEXT: {
                if (v instanceof List<?> || v instanceof Map<?, ?>) c.warn(where + " 得到 " + Values.kind(v) + "，显示为空");
                return visibleText(Values.text(v), c, where, textLimit(spec));
            }
            case STRING: {
                return v instanceof String ? v : drop(c, where, v, spec);
            }
            case ICON: {
                return v instanceof String && NodeParser.ICON_NAMES.contains(((String) v)) ? ((String) v) : drop(c, where, v, spec);
            }
            case IMAGE_SRC: {
                return v instanceof String && imageSrc(((String) v)) ? ((String) v) : drop(c, where, v, spec);
            }
            case TABS: {
                List<Map<String, Object>> tabs = tabs(v);
                return tabs != null ? tabs : drop(c, where, v, spec);
            }
            case ARGS: {
                List<Object> args = args(v);
                return args != null ? args : drop(c, where, v, spec);
            }
            default: {
                return drop(c, where, v, spec);
            }
        }
    }

    private static Object drop(EvalContext c, String where, Object v, Spec spec) {
        c.warn(where + " 得到 " + Values.kind(v) + "，不合规，按没写处理。允许：" + spec.allowed());
        return null;
    }

    /** 超长截断、控制字符换成空格，各记一条 warn。 */
    private static String visibleText(String s, EvalContext c, String where) {
        return visibleText(s, c, where, NodeParser.MAX_TEXT);
    }

    private static int textLimit(Spec spec) { return spec.max > 0 ? spec.max : NodeParser.MAX_TEXT; }

    private static String visibleText(String s, EvalContext c, String where, int max) {
        if (s.length() > max) {
            c.warn(where + " 有 " + s.length() + " 字，截到 " + max);
            s = Values.cut(s, max);
        }
        if (!visible(s)) {
            c.warn(where + " 含控制字符，换成空格");
            StringBuilder sb = new StringBuilder(s);
            for (int i = 0; i < sb.length(); i++) {
                if (control(sb.charAt(i))) sb.setCharAt(i, ' ');
            }
            s = sb.toString();
        }
        return s;
    }

    private static List<Map<String, Object>> tabs(Object v) {
        if (!(v instanceof List<?>) || ((List<?>) v).size() < 2 || ((List<?>) v).size() > 4) return null;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : ((List<?>) v)) {
            if (!(item instanceof Map<?, ?>) || ((Map<?, ?>) item).size() != 1) return null;
            Map.Entry<?, ?> e = ((Map<?, ?>) item).entrySet().iterator().next();
            Object sMatch;
            if (!TAB_FIELDS.contains(e.getKey()) || !((sMatch = e.getValue()) instanceof String)) return null;
            if (e.getKey().equals("icon") ? !NodeParser.ICON_NAMES.contains(((String) sMatch))
                    : e.getKey().equals("text") && (((String) sMatch).length() > NodeParser.MAX_TEXT || !visible(((String) sMatch)))) {
                return null;
            }
            out.add(ImmutableMap.of((String) e.getKey(), ((String) sMatch)));
        }
        return Collections.unmodifiableList(out);
    }

    private static List<Object> args(Object v) {
        if (!(v instanceof List<?>) || ((List<?>) v).size() > NodeParser.MAX_ARGS) return null;
        for (Object item : ((List<?>) v)) {
            if (!(item instanceof String) && !(item instanceof Integer)) return null;
        }
        return Collections.unmodifiableList(new ArrayList<>(((List<?>) v)));
    }

    static boolean visible(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (control(s.charAt(i))) return false;
        }
        return true;
    }

    private static boolean control(char c) {
        return c != '\n' && (c < 0x20 || c == 0x7F);
    }

    private static boolean imageSrc(String s) {
        return s.endsWith(".png") && PackageError.PathRules.accept(s);
    }
}
