package com.november.mcphone.core.script.sfc;

import com.november.mcphone.core.script.engine.ClientScript;
import com.november.mcphone.core.script.layout.NodeType;

import java.util.List;
import java.util.Map;

/**
 * 编译好的模板（施工方案 §9.9）：静态骨架 + 每个动态位置的 AST。装包时编一次、之后常驻；
 * 每次重排由 {@link TemplateInstance} 遍历它、对动态位置求值，产出一棵普通的 Node 树。
 */
public final class CompiledTemplate {

    /** 容器里的一项：元素、一段文字、或一串 v-if / v-else-if / v-else。 */
    interface Child {
    }

    /**
     * 一个元素。vIf 只在带 v-for 时有值（对每一项判断）；不带 v-for 的 v-if 编进 {@link Chain}。
     * staticProps 已按 §5.4 校验过；boundProps 的值要到实例化时才知道。
     */
    static final class Element implements Child {
        private final NodeType type;
        private final int line;
        private final String id;
        private final List<String> classes;
        private final Map<String, Object> staticProps;
        private final Map<String, BoundProp> boundProps;
        private final ForSpec vFor;
        private final Expr.Compiled vIf;
        private final Expr.Compiled key;
        private final Statements click;
        private final Statements input;
        private final List<Child> children;

        Element(NodeType type, int line, String id, List<String> classes, Map<String, Object> staticProps, Map<String, BoundProp> boundProps, ForSpec vFor, Expr.Compiled vIf, Expr.Compiled key, Statements click, Statements input, List<Child> children) {
            this.type = type;
            this.line = line;
            this.id = id;
            this.classes = classes;
            this.staticProps = staticProps;
            this.boundProps = boundProps;
            this.vFor = vFor;
            this.vIf = vIf;
            this.key = key;
            this.click = click;
            this.input = input;
            this.children = children;
        }

        public NodeType type() { return type; }

        public int line() { return line; }

        public String id() { return id; }

        public List<String> classes() { return classes; }

        public Map<String, Object> staticProps() { return staticProps; }

        public Map<String, BoundProp> boundProps() { return boundProps; }

        public ForSpec vFor() { return vFor; }

        public Expr.Compiled vIf() { return vIf; }

        public Expr.Compiled key() { return key; }

        public Statements click() { return click; }

        public Statements input() { return input; }

        public List<Child> children() { return children; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Element)) return false;
            Element that = (Element) other;
            return java.util.Objects.equals(this.type, that.type)
                    && this.line == that.line
                    && java.util.Objects.equals(this.id, that.id)
                    && java.util.Objects.equals(this.classes, that.classes)
                    && java.util.Objects.equals(this.staticProps, that.staticProps)
                    && java.util.Objects.equals(this.boundProps, that.boundProps)
                    && java.util.Objects.equals(this.vFor, that.vFor)
                    && java.util.Objects.equals(this.vIf, that.vIf)
                    && java.util.Objects.equals(this.key, that.key)
                    && java.util.Objects.equals(this.click, that.click)
                    && java.util.Objects.equals(this.input, that.input)
                    && java.util.Objects.equals(this.children, that.children);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(type);
            hash = 31 * hash + java.util.Objects.hashCode(line);
            hash = 31 * hash + java.util.Objects.hashCode(id);
            hash = 31 * hash + java.util.Objects.hashCode(classes);
            hash = 31 * hash + java.util.Objects.hashCode(staticProps);
            hash = 31 * hash + java.util.Objects.hashCode(boundProps);
            hash = 31 * hash + java.util.Objects.hashCode(vFor);
            hash = 31 * hash + java.util.Objects.hashCode(vIf);
            hash = 31 * hash + java.util.Objects.hashCode(key);
            hash = 31 * hash + java.util.Objects.hashCode(click);
            hash = 31 * hash + java.util.Objects.hashCode(input);
            hash = 31 * hash + java.util.Objects.hashCode(children);
            return hash;
        }

        @Override public String toString() { return "Element[type=" + type + ", line=" + line + ", id=" + id + ", classes=" + classes + ", staticProps=" + staticProps + ", boundProps=" + boundProps + ", vFor=" + vFor + ", vIf=" + vIf + ", key=" + key + ", click=" + click + ", input=" + input + ", children=" + children + "]"; }

    }

    static final class BoundProp {
        private final PropRules.Spec spec;
        private final Expr.Compiled expr;

        BoundProp(PropRules.Spec spec, Expr.Compiled expr) {
            this.spec = spec;
            this.expr = expr;
        }

        public PropRules.Spec spec() { return spec; }

        public Expr.Compiled expr() { return expr; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BoundProp)) return false;
            BoundProp that = (BoundProp) other;
            return java.util.Objects.equals(this.spec, that.spec)
                    && java.util.Objects.equals(this.expr, that.expr);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(spec);
            hash = 31 * hash + java.util.Objects.hashCode(expr);
            return hash;
        }

        @Override public String toString() { return "BoundProp[spec=" + spec + ", expr=" + expr + "]"; }

    }

    /** {@code (item, index) in source}；index 可为 null。 */
    static final class ForSpec {
        private final String item;
        private final String index;
        private final Expr.Compiled source;

        ForSpec(String item, String index, Expr.Compiled source) {
            this.item = item;
            this.index = index;
            this.source = source;
        }

        public String item() { return item; }

        public String index() { return index; }

        public Expr.Compiled source() { return source; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ForSpec)) return false;
            ForSpec that = (ForSpec) other;
            return java.util.Objects.equals(this.item, that.item)
                    && java.util.Objects.equals(this.index, that.index)
                    && java.util.Objects.equals(this.source, that.source);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(item);
            hash = 31 * hash + java.util.Objects.hashCode(index);
            hash = 31 * hash + java.util.Objects.hashCode(source);
            return hash;
        }

        @Override public String toString() { return "ForSpec[item=" + item + ", index=" + index + ", source=" + source + "]"; }

    }

    /** 容器里的一段文字，实例化成一个 text 节点。literal 与 expr 恰好一个不为 null。 */
    static final class Text implements Child {
        private final int line;
        private final String literal;
        private final Expr.Compiled expr;

        Text(int line, String literal, Expr.Compiled expr) {
            this.line = line;
            this.literal = literal;
            this.expr = expr;
        }

        public int line() { return line; }

        public String literal() { return literal; }

        public Expr.Compiled expr() { return expr; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Text)) return false;
            Text that = (Text) other;
            return this.line == that.line
                    && java.util.Objects.equals(this.literal, that.literal)
                    && java.util.Objects.equals(this.expr, that.expr);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(line);
            hash = 31 * hash + java.util.Objects.hashCode(literal);
            hash = 31 * hash + java.util.Objects.hashCode(expr);
            return hash;
        }

        @Override public String toString() { return "Text[line=" + line + ", literal=" + literal + ", expr=" + expr + "]"; }

    }

    /** 第一个条件成立的那支进树；conditions 里最后一个为 null 表示 v-else。 */
    static final class Chain implements Child {
        private final List<Expr.Compiled> conditions;
        private final List<Element> branches;

        Chain(List<Expr.Compiled> conditions, List<Element> branches) {
            this.conditions = conditions;
            this.branches = branches;
        }

        public List<Expr.Compiled> conditions() { return conditions; }

        public List<Element> branches() { return branches; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Chain)) return false;
            Chain that = (Chain) other;
            return java.util.Objects.equals(this.conditions, that.conditions)
                    && java.util.Objects.equals(this.branches, that.branches);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(conditions);
            hash = 31 * hash + java.util.Objects.hashCode(branches);
            return hash;
        }

        @Override public String toString() { return "Chain[conditions=" + conditions + ", branches=" + branches + "]"; }

    }

    private final Element root;
    private final Map<String, Object> state;
    private final ClientScript script;

    CompiledTemplate(Element root, Map<String, Object> state, ClientScript script) {
        this.root = root;
        this.state = state;
        this.script = script;
    }

    public ClientScript script() { return script; }

    Element root() {
        return root;
    }

    /** 编译时依据的 state 初值，也是页面打开时的初值。 */
    public Map<String, Object> initialState() {
        return state;
    }
}
