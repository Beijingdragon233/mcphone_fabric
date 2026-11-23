package com.november.mcphone.core.script.layout;

import java.util.List;
import java.util.Map;

/**
 * 一个界面节点。不可变（施工方案 §4.6）。
 *
 * <p>这个类刻意不带任何样式字段：样式全在 Stylesheet 里按 id/class 查。往这里加一个 color
 * 字段，换肤和统一调整当场失效。
 */
public final class Node {
    private final NodeType type;
    private final String id;
    private final List<String> classes;
    private final Map<String, Object> props;
    private final List<Node> children;
    private final ShowIf showIf;
    private final Action onClick;

    public Node(NodeType type, String id, List<String> classes, Map<String, Object> props, List<Node> children, ShowIf showIf, Action onClick) {
        this.type = type;
        this.id = id;
        this.classes = classes;
        this.props = props;
        this.children = children;
        this.showIf = showIf;
        this.onClick = onClick;
    }

    public NodeType type() { return type; }

    public String id() { return id; }

    public List<String> classes() { return classes; }

    public Map<String, Object> props() { return props; }

    public List<Node> children() { return children; }

    public ShowIf showIf() { return showIf; }

    public Action onClick() { return onClick; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Node)) return false;
        Node that = (Node) other;
        return java.util.Objects.equals(this.type, that.type)
                && java.util.Objects.equals(this.id, that.id)
                && java.util.Objects.equals(this.classes, that.classes)
                && java.util.Objects.equals(this.props, that.props)
                && java.util.Objects.equals(this.children, that.children)
                && java.util.Objects.equals(this.showIf, that.showIf)
                && java.util.Objects.equals(this.onClick, that.onClick);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(classes);
        hash = 31 * hash + java.util.Objects.hashCode(props);
        hash = 31 * hash + java.util.Objects.hashCode(children);
        hash = 31 * hash + java.util.Objects.hashCode(showIf);
        hash = 31 * hash + java.util.Objects.hashCode(onClick);
        return hash;
    }

    @Override public String toString() { return "Node[type=" + type + ", id=" + id + ", classes=" + classes + ", props=" + props + ", children=" + children + ", showIf=" + showIf + ", onClick=" + onClick + "]"; }

    public String str(String key, String def) {
        Object v = props.get(key);
        return v instanceof String ? ((String) v) : def;
    }

    public int num(String key, int def) {
        Object v = props.get(key);
        return v instanceof Integer ? ((Integer) v) : def;
    }

    public boolean flag(String key, boolean def) {
        Object v = props.get(key);
        return v instanceof Boolean ? ((Boolean) v) : def;
    }

    /** 条件显示（§4.4）。{@code button} 的 {@code enabledIf} 用的是同一个形状。 */
    public static final class ShowIf {
        private final String key;
        private final Kind kind;
        private final Object value;

        public ShowIf(String key, Kind kind, Object value) {
            this.key = key;
            this.kind = kind;
            this.value = value;
        }

        public String key() { return key; }

        public Kind kind() { return kind; }

        public Object value() { return value; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ShowIf)) return false;
            ShowIf that = (ShowIf) other;
            return java.util.Objects.equals(this.key, that.key)
                    && java.util.Objects.equals(this.kind, that.kind)
                    && java.util.Objects.equals(this.value, that.value);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(key);
            hash = 31 * hash + java.util.Objects.hashCode(kind);
            hash = 31 * hash + java.util.Objects.hashCode(value);
            return hash;
        }

        @Override public String toString() { return "ShowIf[key=" + key + ", kind=" + kind + ", value=" + value + "]"; }

        public enum Kind { EQ, NE, TRUTHY }
    }

    /**
     * 点击动作（§4.5）。执行顺序固定 set → toggle → nav。
     *
     * <p>{@code builtin} 是字符串形式的 {@code "close"}/{@code "back"}，与另外三个互斥。
     */
    public static final class Action {
        private final Map<String, Object> set;
        private final String toggle;
        private final String nav;
        private final Builtin builtin;

        public Action(Map<String, Object> set, String toggle, String nav, Builtin builtin) {
            this.set = set;
            this.toggle = toggle;
            this.nav = nav;
            this.builtin = builtin;
        }

        public Map<String, Object> set() { return set; }

        public String toggle() { return toggle; }

        public String nav() { return nav; }

        public Builtin builtin() { return builtin; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Action)) return false;
            Action that = (Action) other;
            return java.util.Objects.equals(this.set, that.set)
                    && java.util.Objects.equals(this.toggle, that.toggle)
                    && java.util.Objects.equals(this.nav, that.nav)
                    && java.util.Objects.equals(this.builtin, that.builtin);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(set);
            hash = 31 * hash + java.util.Objects.hashCode(toggle);
            hash = 31 * hash + java.util.Objects.hashCode(nav);
            hash = 31 * hash + java.util.Objects.hashCode(builtin);
            return hash;
        }

        @Override public String toString() { return "Action[set=" + set + ", toggle=" + toggle + ", nav=" + nav + ", builtin=" + builtin + "]"; }

        public enum Builtin { NONE, CLOSE, BACK }
    }
}
