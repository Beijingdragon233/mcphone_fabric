package com.november.mcphone.core.script.sfc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表达式的 AST（施工方案 §9.5）。由 {@link ExprParser} 在编译期建好并缓存，重排时只遍历、不重新解析。
 *
 * <p>求值不向外抛：类型不对、嵌套过深时整条表达式得 null 并记一条 warn。
 */
interface Expr {

    Object eval(EvalContext c);

    /** 一条编译好的表达式：根节点与它在原文件里的行。 */
    final class Compiled {
        private final Expr root;
        private final int line;
        private final String source;

        public Compiled(Expr root, int line, String source) {
            this.root = root;
            this.line = line;
            this.source = source;
        }

        public Expr root() { return root; }

        public int line() { return line; }

        public String source() { return source; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Compiled)) return false;
            Compiled that = (Compiled) other;
            return java.util.Objects.equals(this.root, that.root)
                    && this.line == that.line
                    && java.util.Objects.equals(this.source, that.source);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(root);
            hash = 31 * hash + java.util.Objects.hashCode(line);
            hash = 31 * hash + java.util.Objects.hashCode(source);
            return hash;
        }

        @Override public String toString() { return "Compiled[root=" + root + ", line=" + line + ", source=" + source + "]"; }


        Object run(EvalContext c) {
            if (!c.budget()) return null;
            c.line = line;
            c.depth = 0;
            try {
                return root.eval(c);
            } catch (Abort a) {
                c.warn(a.getMessage());
                return null;
            }
        }
    }

    /** 整条表达式放弃求值。不带栈：它是控制流，每帧都可能抛。 */
    final class Abort extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Abort(String reason) {
            super(reason, null, false, false);
        }

        static Abort type(String op, Object a, Object b) {
            String body = SfcError.Code.E_EXPR_TYPE.text().substring("%s:%d ".length());
            return new Abort(String.format(java.util.Locale.ROOT, body, op, Values.kind(a), b == NONE ? "（一元）" : Values.kind(b)));
        }
    }

    /** 一元运算报类型错时占右边那一格。 */
    Object NONE = new Object();

    static void enter(EvalContext c) {
        if (++c.depth > EvalContext.MAX_DEPTH) throw new Abort("表达式求值超过 " + EvalContext.MAX_DEPTH + " 层");
    }

    final class Lit implements Expr {
        private final Object value;

        public Lit(Object value) {
            this.value = value;
        }

        public Object value() { return value; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Lit)) return false;
            Lit that = (Lit) other;
            return java.util.Objects.equals(this.value, that.value);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(value);
            return hash;
        }

        @Override public String toString() { return "Lit[value=" + value + "]"; }

        public Object eval(EvalContext c) {
            return value;
        }
    }

    final class Ref implements Expr {
        private final String name;

        public Ref(String name) {
            this.name = name;
        }

        public String name() { return name; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Ref)) return false;
            Ref that = (Ref) other;
            return java.util.Objects.equals(this.name, that.name);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(name);
            return hash;
        }

        @Override public String toString() { return "Ref[name=" + name + "]"; }

        public Object eval(EvalContext c) {
            return c.lookup(name);
        }
    }

    /** a.b：a 是对象且有这个键才有值；.length 只对数组与字符串；其余都是 null，不报错（§9.5.5）。 */
    final class Member implements Expr {
        private final Expr target;
        private final String name;

        public Member(Expr target, String name) {
            this.target = target;
            this.name = name;
        }

        public Expr target() { return target; }

        public String name() { return name; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Member)) return false;
            Member that = (Member) other;
            return java.util.Objects.equals(this.target, that.target)
                    && java.util.Objects.equals(this.name, that.name);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(target);
            hash = 31 * hash + java.util.Objects.hashCode(name);
            return hash;
        }

        @Override public String toString() { return "Member[target=" + target + ", name=" + name + "]"; }

        public Object eval(EvalContext c) {
            enter(c);
            Object v = target.eval(c);
            c.depth--;
            if (v instanceof Map<?, ?>) return ((Map<?, ?>) v).get(name);
            if (name.equals("length")) {
                if (v instanceof List<?>) return ((List<?>) v).size();
                if (v instanceof String) return ((String) v).length();
            }
            return null;
        }
    }

    final class Index implements Expr {
        private final Expr target;
        private final Expr index;

        public Index(Expr target, Expr index) {
            this.target = target;
            this.index = index;
        }

        public Expr target() { return target; }

        public Expr index() { return index; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Index)) return false;
            Index that = (Index) other;
            return java.util.Objects.equals(this.target, that.target)
                    && java.util.Objects.equals(this.index, that.index);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(target);
            hash = 31 * hash + java.util.Objects.hashCode(index);
            return hash;
        }

        @Override public String toString() { return "Index[target=" + target + ", index=" + index + "]"; }

        public Object eval(EvalContext c) {
            enter(c);
            Object v = target.eval(c);
            Object i = index.eval(c);
            c.depth--;
            if (v instanceof List<?> && i instanceof Integer && ((Integer) i) >= 0 && ((Integer) i) < ((List<?>) v).size()) return ((List<?>) v).get(((Integer) i));
            return null;
        }
    }

    final class Unary implements Expr {
        private final char op;
        private final Expr operand;

        public Unary(char op, Expr operand) {
            this.op = op;
            this.operand = operand;
        }

        public char op() { return op; }

        public Expr operand() { return operand; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Unary)) return false;
            Unary that = (Unary) other;
            return this.op == that.op
                    && java.util.Objects.equals(this.operand, that.operand);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(op);
            hash = 31 * hash + java.util.Objects.hashCode(operand);
            return hash;
        }

        @Override public String toString() { return "Unary[op=" + op + ", operand=" + operand + "]"; }

        public Object eval(EvalContext c) {
            enter(c);
            Object v = operand.eval(c);
            c.depth--;
            if (op == '!') return !Values.truthy(v);
            if (v instanceof Integer) return -((Integer) v);
            throw Abort.type("-", v, NONE);
        }
    }

    final class Binary implements Expr {
        private final String op;
        private final Expr left;
        private final Expr right;

        public Binary(String op, Expr left, Expr right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }

        public String op() { return op; }

        public Expr left() { return left; }

        public Expr right() { return right; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Binary)) return false;
            Binary that = (Binary) other;
            return java.util.Objects.equals(this.op, that.op)
                    && java.util.Objects.equals(this.left, that.left)
                    && java.util.Objects.equals(this.right, that.right);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(op);
            hash = 31 * hash + java.util.Objects.hashCode(left);
            hash = 31 * hash + java.util.Objects.hashCode(right);
            return hash;
        }

        @Override public String toString() { return "Binary[op=" + op + ", left=" + left + ", right=" + right + "]"; }

        public Object eval(EvalContext c) {
            enter(c);
            Object a = left.eval(c);
            Object b = right.eval(c);
            c.depth--;
            switch (op) {
                case "==":
                    return Values.same(a, b);
                case "!=":
                    return !Values.same(a, b);
                case "+":
                    if (a instanceof Integer && b instanceof Integer) return ((Integer) a) + ((Integer) b);
                    if (a instanceof String || b instanceof String) {
                        Object other = a instanceof String ? b : a;
                        if (other instanceof List<?> || other instanceof Map<?, ?>) {
                            c.warn(Values.kind(other) + " 拼进字符串显示为空");
                        }
                        return c.capped(Values.text(a) + Values.text(b));
                    }
                    throw Abort.type(op, a, b);
                default:
                    break;
            }
            if (a instanceof Integer && b instanceof Integer) {
                switch (op) {
                    case "-":
                        return ((Integer) a) - ((Integer) b);
                    case "*":
                        return ((Integer) a) * ((Integer) b);
                    case "/":
                    case "%":
                        if (((Integer) b) == 0) {
                            c.warn("除以 0，结果按 0 算");
                            return 0;
                        }
                        return op.equals("/") ? ((Integer) a) / ((Integer) b) : ((Integer) a) % ((Integer) b);
                    case "<":
                        return ((Integer) a) < ((Integer) b);
                    case "<=":
                        return ((Integer) a) <= ((Integer) b);
                    case ">":
                        return ((Integer) a) > ((Integer) b);
                    default:
                        return ((Integer) a) >= ((Integer) b);
                }
            }
            if (a instanceof String && b instanceof String && isRelational(op)) {
                int cmp = ((String) a).compareTo(((String) b));
                switch (op) {
                    case "<":
                        return cmp < 0;
                    case "<=":
                        return cmp <= 0;
                    case ">":
                        return cmp > 0;
                    default:
                        return cmp >= 0;
                }
            }
            throw Abort.type(op, a, b);
        }

        static boolean isRelational(String op) {
            return op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=");
        }
    }

    /** && 与 || 短路，返回 bool 而不是操作数本身（§9.5.3）。 */
    final class Logic implements Expr {
        private final boolean and;
        private final Expr left;
        private final Expr right;

        public Logic(boolean and, Expr left, Expr right) {
            this.and = and;
            this.left = left;
            this.right = right;
        }

        public boolean and() { return and; }

        public Expr left() { return left; }

        public Expr right() { return right; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Logic)) return false;
            Logic that = (Logic) other;
            return this.and == that.and
                    && java.util.Objects.equals(this.left, that.left)
                    && java.util.Objects.equals(this.right, that.right);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(and);
            hash = 31 * hash + java.util.Objects.hashCode(left);
            hash = 31 * hash + java.util.Objects.hashCode(right);
            return hash;
        }

        @Override public String toString() { return "Logic[and=" + and + ", left=" + left + ", right=" + right + "]"; }

        public Object eval(EvalContext c) {
            enter(c);
            boolean l = Values.truthy(left.eval(c));
            boolean out = and ? l && Values.truthy(right.eval(c)) : l || Values.truthy(right.eval(c));
            c.depth--;
            return out;
        }
    }

    final class Cond implements Expr {
        private final Expr test;
        private final Expr then;
        private final Expr otherwise;

        public Cond(Expr test, Expr then, Expr otherwise) {
            this.test = test;
            this.then = then;
            this.otherwise = otherwise;
        }

        public Expr test() { return test; }

        public Expr then() { return then; }

        public Expr otherwise() { return otherwise; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Cond)) return false;
            Cond that = (Cond) other;
            return java.util.Objects.equals(this.test, that.test)
                    && java.util.Objects.equals(this.then, that.then)
                    && java.util.Objects.equals(this.otherwise, that.otherwise);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(test);
            hash = 31 * hash + java.util.Objects.hashCode(then);
            hash = 31 * hash + java.util.Objects.hashCode(otherwise);
            return hash;
        }

        @Override public String toString() { return "Cond[test=" + test + ", then=" + then + ", otherwise=" + otherwise + "]"; }

        public Object eval(EvalContext c) {
            enter(c);
            Object v = Values.truthy(test.eval(c)) ? then.eval(c) : otherwise.eval(c);
            c.depth--;
            return v;
        }
    }

    final class ArrayLit implements Expr {
        private final List<Expr> items;

        public ArrayLit(List<Expr> items) {
            this.items = items;
        }

        public List<Expr> items() { return items; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ArrayLit)) return false;
            ArrayLit that = (ArrayLit) other;
            return java.util.Objects.equals(this.items, that.items);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(items);
            return hash;
        }

        @Override public String toString() { return "ArrayLit[items=" + items + "]"; }

        public Object eval(EvalContext c) {
            enter(c);
            List<Object> out = new ArrayList<>(items.size());
            for (Expr e : items) out.add(e.eval(c));
            c.depth--;
            return Collections.unmodifiableList(out);
        }
    }

    final class ObjectLit implements Expr {
        private final List<String> keys;
        private final List<Expr> values;

        public ObjectLit(List<String> keys, List<Expr> values) {
            this.keys = keys;
            this.values = values;
        }

        public List<String> keys() { return keys; }

        public List<Expr> values() { return values; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ObjectLit)) return false;
            ObjectLit that = (ObjectLit) other;
            return java.util.Objects.equals(this.keys, that.keys)
                    && java.util.Objects.equals(this.values, that.values);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(keys);
            hash = 31 * hash + java.util.Objects.hashCode(values);
            return hash;
        }

        @Override public String toString() { return "ObjectLit[keys=" + keys + ", values=" + values + "]"; }

        public Object eval(EvalContext c) {
            enter(c);
            Map<String, Object> out = new LinkedHashMap<>();
            for (int i = 0; i < keys.size(); i++) out.put(keys.get(i), values.get(i).eval(c));
            c.depth--;
            return Collections.unmodifiableMap(out);
        }
    }

    /**
     * 插值：各段文本化后拼起来。不走 +：{{ a }}{{ b }} 两个 int 该拼成 "12"，不是加成 3。
     * 第二个起每个 {{ }} 各记一次求值（第一个已由 Compiled.run 记过）：一段文字里塞几万个插值时，整段只算一次会绕过 4096 的上限。
     */
    final class Concat implements Expr {
        private final List<Expr> parts;

        public Concat(List<Expr> parts) {
            this.parts = parts;
        }

        public List<Expr> parts() { return parts; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Concat)) return false;
            Concat that = (Concat) other;
            return java.util.Objects.equals(this.parts, that.parts);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(parts);
            return hash;
        }

        @Override public String toString() { return "Concat[parts=" + parts + "]"; }

        public Object eval(EvalContext c) {
            enter(c);
            StringBuilder sb = new StringBuilder();
            boolean charged = false;
            for (Expr p : parts) {
                Object v;
                if (p instanceof Lit) {
                    v = p.eval(c);
                } else if (!charged) {
                    charged = true;
                    v = p.eval(c);
                } else {
                    v = c.budget() ? p.eval(c) : null;
                }
                if (v instanceof List<?> || v instanceof Map<?, ?>) c.warn(Values.kind(v) + " 放进文字里显示为空");
                sb.append(Values.text(v));
                if (sb.length() > EvalContext.MAX_CONCAT) break;
            }
            c.depth--;
            return c.capped(sb.toString());
        }
    }
}
