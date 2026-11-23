package com.november.mcphone.core.script.sfc;

import com.november.mcphone.util.Java8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.november.mcphone.util.Java8Collections;

import com.november.mcphone.core.script.engine.ClientScript;
import com.november.mcphone.core.script.layout.NodeParser;
import com.november.mcphone.core.script.layout.NodeType;
import com.november.mcphone.core.script.layout.StateRules;
import com.november.mcphone.core.script.sfc.CompiledTemplate.BoundProp;
import com.november.mcphone.core.script.sfc.CompiledTemplate.Chain;
import com.november.mcphone.core.script.sfc.CompiledTemplate.Child;
import com.november.mcphone.core.script.sfc.CompiledTemplate.Element;
import com.november.mcphone.core.script.sfc.CompiledTemplate.ForSpec;
import com.november.mcphone.core.script.sfc.CompiledTemplate.Text;
import com.november.mcphone.core.script.sfc.ExprParser.Scope;
import com.november.mcphone.core.script.sfc.ExprParser.T;
import com.november.mcphone.core.script.sfc.ExprParser.Typed;
import com.november.mcphone.core.script.sfc.SfcError.Code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code <template>} → {@link CompiledTemplate}（施工方案 §9.4）。先按类 HTML 的写法读成原始树，再逐元素做语义检查。
 * 行号是块内行号，由 {@link SfcCompiler} 加偏移。
 */
public final class TemplateCompiler {

    static final int MAX_FOR_NESTING = 2;

    /**
     * 模板里元素的个数上限，只防超大文件把编译拖垮。512 个节点的上限在实例化时查（§9.9）：
     * 互斥的 v-if 分支加起来可以超过 512，而任何时刻进树的不超过。
     */
    static final int MAX_TEMPLATE_ELEMENTS = 4096;

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]{0,31}");
    private static final Pattern CLASS_NAME = Pattern.compile("[a-z][a-z0-9_-]{0,31}");
    private static final Pattern FOR = Pattern.compile(
            "\\s*(?:\\(\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:,\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*)?\\)|([A-Za-z_$][A-Za-z0-9_$]*))\\s+in\\s+",
            Pattern.DOTALL);
    private static final Set<String> KEYWORDS = ImmutableSet.of("true", "false", "null", "in");
    /** Vue 的 condense 只压 ASCII 空白；全角空格、&nbsp; 与 &#10; 解出来的换行都是作者有意写的。 */
    private static final Pattern ASCII_SPACE = Pattern.compile("[ \\t\\n\\r\\f]+");
    private static final Set<NodeType> CLICKABLE = ImmutableSet.of(NodeType.BUTTON, NodeType.TOGGLE, NodeType.TAB_BAR);
    private static final List<String> ELEMENT_NAMES = Arrays.stream(NodeType.values()).map(t -> t.json).collect(Java8Collections.toList());

    // ============================================================
    //  原始树
    // ============================================================

    /** value 是解码实体之后的值，raw 是原文：表达式按 raw 量长度、按 raw 换算报错位置。 */
    static final class Attr {
        private final String name;
        private final String value;
        private final String raw;
        private final int line;
        private final int col;
        private final int valueLine;
        private final int valueCol;

        Attr(String name, String value, String raw, int line, int col, int valueLine, int valueCol) {
            this.name = name;
            this.value = value;
            this.raw = raw;
            this.line = line;
            this.col = col;
            this.valueLine = valueLine;
            this.valueCol = valueCol;
        }

        public String name() { return name; }

        public String value() { return value; }

        public String raw() { return raw; }

        public int line() { return line; }

        public int col() { return col; }

        public int valueLine() { return valueLine; }

        public int valueCol() { return valueCol; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Attr)) return false;
            Attr that = (Attr) other;
            return java.util.Objects.equals(this.name, that.name)
                    && java.util.Objects.equals(this.value, that.value)
                    && java.util.Objects.equals(this.raw, that.raw)
                    && this.line == that.line
                    && this.col == that.col
                    && this.valueLine == that.valueLine
                    && this.valueCol == that.valueCol;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(name);
            hash = 31 * hash + java.util.Objects.hashCode(value);
            hash = 31 * hash + java.util.Objects.hashCode(raw);
            hash = 31 * hash + java.util.Objects.hashCode(line);
            hash = 31 * hash + java.util.Objects.hashCode(col);
            hash = 31 * hash + java.util.Objects.hashCode(valueLine);
            hash = 31 * hash + java.util.Objects.hashCode(valueCol);
            return hash;
        }

        @Override public String toString() { return "Attr[name=" + name + ", value=" + value + ", raw=" + raw + ", line=" + line + ", col=" + col + ", valueLine=" + valueLine + ", valueCol=" + valueCol + "]"; }

    }

    /** 文字里的一段：literal 不为 null 是字面文字，否则是 {{ }} 里的表达式源码。 */
    static final class Piece {
        private final String literal;
        private final String expr;
        private final int line;
        private final int col;

        Piece(String literal, String expr, int line, int col) {
            this.literal = literal;
            this.expr = expr;
            this.line = line;
            this.col = col;
        }

        public String literal() { return literal; }

        public String expr() { return expr; }

        public int line() { return line; }

        public int col() { return col; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Piece)) return false;
            Piece that = (Piece) other;
            return java.util.Objects.equals(this.literal, that.literal)
                    && java.util.Objects.equals(this.expr, that.expr)
                    && this.line == that.line
                    && this.col == that.col;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(literal);
            hash = 31 * hash + java.util.Objects.hashCode(expr);
            hash = 31 * hash + java.util.Objects.hashCode(line);
            hash = 31 * hash + java.util.Objects.hashCode(col);
            return hash;
        }

        @Override public String toString() { return "Piece[literal=" + literal + ", expr=" + expr + ", line=" + line + ", col=" + col + "]"; }

    }

    interface Raw {
    }

    static final class RawElement implements Raw {
        private final String name;
        private final NodeType type;
        private final int line;
        private final int col;
        private final List<Attr> attrs;
        private final boolean selfClosed;
        private final List<Raw> children;

        RawElement(String name, NodeType type, int line, int col, List<Attr> attrs, boolean selfClosed, List<Raw> children) {
            this.name = name;
            this.type = type;
            this.line = line;
            this.col = col;
            this.attrs = attrs;
            this.selfClosed = selfClosed;
            this.children = children;
        }

        public String name() { return name; }

        public NodeType type() { return type; }

        public int line() { return line; }

        public int col() { return col; }

        public List<Attr> attrs() { return attrs; }

        public boolean selfClosed() { return selfClosed; }

        public List<Raw> children() { return children; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RawElement)) return false;
            RawElement that = (RawElement) other;
            return java.util.Objects.equals(this.name, that.name)
                    && java.util.Objects.equals(this.type, that.type)
                    && this.line == that.line
                    && this.col == that.col
                    && java.util.Objects.equals(this.attrs, that.attrs)
                    && this.selfClosed == that.selfClosed
                    && java.util.Objects.equals(this.children, that.children);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(name);
            hash = 31 * hash + java.util.Objects.hashCode(type);
            hash = 31 * hash + java.util.Objects.hashCode(line);
            hash = 31 * hash + java.util.Objects.hashCode(col);
            hash = 31 * hash + java.util.Objects.hashCode(attrs);
            hash = 31 * hash + java.util.Objects.hashCode(selfClosed);
            hash = 31 * hash + java.util.Objects.hashCode(children);
            return hash;
        }

        @Override public String toString() { return "RawElement[name=" + name + ", type=" + type + ", line=" + line + ", col=" + col + ", attrs=" + attrs + ", selfClosed=" + selfClosed + ", children=" + children + "]"; }

    }

    static final class RawText implements Raw {
        private final int line;
        private final List<Piece> pieces;

        RawText(int line, List<Piece> pieces) {
            this.line = line;
            this.pieces = pieces;
        }

        public int line() { return line; }

        public List<Piece> pieces() { return pieces; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RawText)) return false;
            RawText that = (RawText) other;
            return this.line == that.line
                    && java.util.Objects.equals(this.pieces, that.pieces);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(line);
            hash = 31 * hash + java.util.Objects.hashCode(pieces);
            return hash;
        }

        @Override public String toString() { return "RawText[line=" + line + ", pieces=" + pieces + "]"; }

        /** 只有 ASCII 空白才算空：全角空格是作者写的内容。 */
        boolean blank() {
            for (Piece p : pieces) {
                if (p.expr != null || !ASCII_SPACE.matcher(p.literal).replaceAll("").isEmpty()) return false;
            }
            return true;
        }
    }

    static final class RawComment implements Raw {
        RawComment() {
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RawComment)) return false;
            return true;
        }

        @Override public int hashCode() {
            return 0;
        }

        @Override public String toString() { return "RawComment[]"; }

    }

    private final String src;
    private final int lineOffset;
    private final ClientScript script;
    private int pos;
    private int line = 1;
    private int col = 1;
    private int elements;

    private TemplateCompiler(String src, int lineOffset, ClientScript script) {
        this.src = src;
        this.lineOffset = lineOffset;
        this.script = script;
    }

    /** 编译一个 {@code <template>} 块的内容。state 是 {@code <script>} 给的初值，决定哪些名字声明过。 */
    public static CompiledTemplate compile(String content, Map<String, Object> state) {
        return compile(content, state, 0);
    }

    /**
     * lineOffset 加在运行期 warn 的行号上（块内行 → 原文件行，§9.8）。编译期抛的错仍是块内行号，由 {@link SfcCompiler} 统一加偏移。
     */
    public static CompiledTemplate compile(String content, Map<String, Object> state, int lineOffset) {
        return compile(content, state, lineOffset, null);
    }

    static CompiledTemplate compile(String content, Map<String, Object> state, int lineOffset, ClientScript script) {
        Objects.requireNonNull(content, "content");
        TemplateCompiler c = new TemplateCompiler(content, lineOffset, script);
        RawElement root = c.document();
        Map<String, Object> declared = Collections.unmodifiableMap(new LinkedHashMap<>(state));
        Element compiled = c.element(root, Scope.of(declared), 0, 1);
        return new CompiledTemplate(compiled, declared, script);
    }

    private RawElement document() {
        RawElement root = null;
        while (true) {
            skipBlank();
            if (pos >= src.length()) break;
            if (src.startsWith("<!--", pos)) {
                comment();
                continue;
            }
            if (src.charAt(pos) != '<') throw syntax(line, col, "根元素外面不能有文字");
            if (src.startsWith("</", pos)) throw syntax(line, col, "多余的 " + closingName());
            if (root != null) throw syntax(line, col, "<template> 里只能有一个根元素");
            root = rawElement(new ArrayList<>());
        }
        if (root == null) throw syntax(1, 1, "<template> 里没有元素");
        for (Attr a : root.attrs) {
            if (a.name.equals("v-if") || a.name.equals("v-else-if") || a.name.equals("v-else") || a.name.equals("v-for")) {
                throw syntax(a.line, a.col, "根元素不能写 " + a.name + "：一页只有一个根");
            }
        }
        return root;
    }

    private RawElement rawElement(List<String> ancestors) {
        int tagLine = line;
        int tagCol = col;
        advance();   // <
        int nameStart = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '-')
                && src.charAt(pos) < 0x80) {
            advance();
        }
        String name = src.substring(nameStart, pos);
        if (name.isEmpty() || !Character.isLetter(name.charAt(0))) {
            throw syntax(tagLine, tagCol, "'<' 后面要写元素名（文字里的 < 写成 &lt;）");
        }
        NodeType type = NodeType.of(name);
        if (type == null) {
            throw SfcError.at(Code.E_TPL_UNKNOWN_ELEMENT, tagLine, tagCol, name, Names.hint(name, ELEMENT_NAMES),
                    String.join(" ", ELEMENT_NAMES));
        }
        if (++elements > MAX_TEMPLATE_ELEMENTS) throw syntax(tagLine, tagCol, "模板里的元素超过 " + MAX_TEMPLATE_ELEMENTS + " 个");
        if (ancestors.size() >= NodeParser.MAX_DEPTH) throw syntax(tagLine, tagCol, "元素嵌套超过 " + NodeParser.MAX_DEPTH + " 层");

        List<Attr> attrs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        boolean selfClosed;
        while (true) {
            skipBlank();
            if (pos >= src.length()) throw SfcError.at(Code.E_TPL_UNCLOSED_TAG, tagLine, tagCol, name);
            if (src.startsWith("/>", pos)) {
                advance();
                advance();
                selfClosed = true;
                break;
            }
            if (src.charAt(pos) == '>') {
                advance();
                selfClosed = false;
                break;
            }
            Attr a = attr(name, tagLine, tagCol);
            if (!seen.add(a.name)) throw syntax(a.line, a.col, "属性 '" + a.name + "' 写了两次");
            attrs.add(a);
        }

        List<Raw> children = new ArrayList<>();
        if (!selfClosed) {
            ancestors.add(name);
            while (true) {
                if (pos >= src.length()) throw SfcError.at(Code.E_TPL_UNCLOSED_TAG, tagLine, tagCol, name);
                if (src.startsWith("<!--", pos)) {
                    comment();
                    children.add(new RawComment());
                } else if (src.startsWith("</", pos)) {
                    int closeLine = line;
                    int closeCol = col;
                    String closing = closingName();
                    if (closing.equals(name)) break;
                    // 闭的是外层的名字：这一层忘了闭。报这一层的开标签，那才是要改的地方
                    if (ancestors.subList(0, ancestors.size() - 1).contains(closing)) {
                        throw SfcError.at(Code.E_TPL_UNCLOSED_TAG, tagLine, tagCol, name);
                    }
                    throw syntax(closeLine, closeCol, "多余的 </" + closing + ">");
                } else if (src.charAt(pos) == '<') {
                    children.add(rawElement(ancestors));
                } else {
                    children.add(text());
                }
            }
            ancestors.remove(ancestors.size() - 1);
        }
        return new RawElement(name, type, tagLine, tagCol, ImmutableList.copyOf(attrs), selfClosed, ImmutableList.copyOf(children));
    }

    private Attr attr(String tag, int tagLine, int tagCol) {
        int aLine = line;
        int aCol = col;
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isWhitespace(c) || c == '=' || c == '>' || c == '"' || c == '\'' || c == '<'
                    || (c == '/' && src.startsWith("/>", pos))) {
                break;
            }
            advance();
        }
        String name = src.substring(start, pos);
        if (name.isEmpty()) throw syntax(aLine, aCol, "<" + tag + "> 的属性写法不对：'" + ExprParser.shown(src.charAt(pos)) + "'");
        int save = pos;
        int saveLine = line;
        int saveCol = col;
        skipBlank();
        if (pos >= src.length() || src.charAt(pos) != '=') {
            pos = save;
            line = saveLine;
            col = saveCol;
            return new Attr(name, null, null, aLine, aCol, aLine, aCol);
        }
        advance();
        skipBlank();
        if (pos >= src.length()) throw SfcError.at(Code.E_TPL_UNCLOSED_TAG, tagLine, tagCol, tag);
        char q = src.charAt(pos);
        if (q == '"' || q == '\'') {
            advance();
            int vLine = line;
            int vCol = col;
            int vStart = pos;
            while (pos < src.length() && src.charAt(pos) != q) advance();
            if (pos >= src.length()) throw syntax(aLine, aCol, "属性 '" + name + "' 的值没有收尾的引号");
            // 属性值里的实体与文字内容里一样解码：:enabled="count &lt; 1" 该是 <，不是位运算
            String rawValue = src.substring(vStart, pos);
            advance();
            return new Attr(name, decode(rawValue), rawValue, aLine, aCol, vLine, vCol);
        }
        int vLine = line;
        int vCol = col;
        int vStart = pos;
        while (pos < src.length() && !Character.isWhitespace(src.charAt(pos)) && src.charAt(pos) != '>'
                && !src.startsWith("/>", pos)) {
            advance();
        }
        String rawValue = src.substring(vStart, pos);
        return new Attr(name, decode(rawValue), rawValue, aLine, aCol, vLine, vCol);
    }

    /** 一段文字，读到下一个 '<' 为止；{{ }} 里的 '<' 属于表达式。 */
    private RawText text() {
        int startLine = line;
        List<Piece> pieces = new ArrayList<>();
        StringBuilder lit = new StringBuilder();
        int litLine = line;
        int litCol = col;
        while (pos < src.length() && src.charAt(pos) != '<') {
            if (src.startsWith("{{", pos)) {
                if (lit.length() > 0) pieces.add(new Piece(lit.toString(), null, litLine, litCol));
                lit.setLength(0);
                int openLine = line;
                int openCol = col;
                advance();
                advance();
                int eLine = line;
                int eCol = col;
                int eStart = pos;
                while (pos < src.length() && !src.startsWith("}}", pos)) advance();
                if (pos >= src.length()) throw syntax(openLine, openCol, "{{ 没有收尾的 }}");
                pieces.add(new Piece(null, src.substring(eStart, pos), eLine, eCol));
                advance();
                advance();
                litLine = line;
                litCol = col;
            } else {
                lit.append(src.charAt(pos));
                advance();
            }
        }
        if (lit.length() > 0) pieces.add(new Piece(lit.toString(), null, litLine, litCol));
        return new RawText(startLine, pieces);
    }

    private void comment() {
        int cLine = line;
        int cCol = col;
        int end = src.indexOf("-->", pos + 4);
        if (end < 0) throw syntax(cLine, cCol, "<!-- 注释没有收尾");
        while (pos < end + 3) advance();
    }

    /** 读一个 {@code </name>}，返回 name。 */
    private String closingName() {
        int cLine = line;
        int cCol = col;
        advance();
        advance();
        int start = pos;
        while (pos < src.length() && src.charAt(pos) != '>' && src.charAt(pos) != '<' && src.charAt(pos) != '\n') advance();
        if (pos >= src.length() || src.charAt(pos) != '>') throw syntax(cLine, cCol, "闭标签没有写完");
        String name = Java8.strip(src.substring(start, pos));
        advance();
        return name;
    }

    private void skipBlank() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) advance();
    }

    private void advance() {
        if (src.charAt(pos) == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        pos++;
    }

    // ============================================================
    //  语义
    // ============================================================

    /** forDepth 是外层 v-for 的层数。depth 仅用于报错信息之外的防御：原始树已经限过深度。 */
    private Element element(RawElement raw, Scope scope, int forDepth, int depth) {
        NodeType type = raw.type;
        String tag = raw.name;
        Map<String, PropRules.Spec> specs = PropRules.of(type);

        Attr vIf = null;
        Attr vElseIf = null;
        Attr vElse = null;
        Attr vFor = null;
        Attr key = null;
        Attr click = null;
        Attr input = null;
        Attr id = null;
        Attr cls = null;
        List<Attr> statics = new ArrayList<>();
        List<Attr> bounds = new ArrayList<>();
        for (Attr a : raw.attrs) {
            switch (a.name) {
                case "v-if":
                    vIf = a;
                    break;
                case "v-else-if":
                    vElseIf = a;
                    break;
                case "v-else":
                    vElse = a;
                    break;
                case "v-for":
                    vFor = a;
                    break;
                case ":key":
                    key = a;
                    break;
                case "id":
                    id = a;
                    break;
                case "class":
                    cls = a;
                    break;
                default: {
                    if (a.name.startsWith("@")) {
                        if (a.name.equals("@input")) {
                            if (!type.textEditor()) throw badValue(tag, a, "@input 只支持 text-input 和 textarea");
                            input = a;
                        } else if (a.name.equals("@click")) {
                            if (!CLICKABLE.contains(type)) throw SfcError.at(Code.E_TPL_EVENT_NOT_ALLOWED, a.line, a.col, tag);
                            click = a;
                        } else throw unknownAttr(raw, a, specs);
                    } else if (a.name.startsWith(":")) {
                        PropRules.Spec spec = specs.get(a.name.substring(1));
                        if (spec == null || spec.staticOnly()) throw unknownAttr(raw, a, specs);
                        bounds.add(a);
                    } else {
                        PropRules.Spec spec = specs.get(a.name);
                        if (spec == null || spec.boundOnly()) throw unknownAttr(raw, a, specs);
                        statics.add(a);
                    }
                }
                    break;
            }
        }

        int directives = (vIf != null ? 1 : 0) + (vElseIf != null ? 1 : 0) + (vElse != null ? 1 : 0);
        if (directives > 1) throw syntax(raw.line, raw.col, "v-if / v-else-if / v-else 一个元素只能写一个");
        if (vFor != null && (vElse != null || vElseIf != null)) {
            throw syntax(vFor.line, vFor.col, "v-for 不能和 v-else / v-else-if 写在同一个元素上");
        }
        for (Attr a : new Attr[]{vIf, vElseIf, vFor, key, click, input}) {
            if (a != null && (a.value == null || Java8.isBlank(a.value))) throw syntax(a.line, a.col, a.name + " 要写值");
        }
        if (vElse != null && vElse.value != null) throw syntax(vElse.line, vElse.col, "v-else 不写值");
        for (Attr b : bounds) {
            if (b.value == null || Java8.isBlank(b.value)) throw syntax(b.line, b.col, b.name + " 要写表达式");
            for (Attr s : statics) {
                if (s.name.equals(b.name.substring(1))) throw syntax(b.line, b.col, s.name + " 和 " + b.name + " 只能写一个");
            }
        }

        // v-for：来源在外层作用域里求值，循环变量只在这个元素的子树里有效
        ForSpec forSpec = null;
        Scope inner = scope;
        if (vFor != null) {
            if (forDepth + 1 > MAX_FOR_NESTING) throw SfcError.at(Code.E_TPL_NESTED_FOR, vFor.line, vFor.col);
            Matcher m = FOR.matcher(vFor.value);
            if (!m.lookingAt()) throw syntax(vFor.line, vFor.col, "v-for 写成 x in 数组，或 (x, i) in 数组");
            String item = m.group(3) != null ? m.group(3) : m.group(1);
            String index = m.group(2);
            for (String n : index == null ? ImmutableList.of(item) : ImmutableList.of(item, index)) {
                if (KEYWORDS.contains(n)) throw syntax(vFor.line, vFor.col, "'" + n + "' 不能当 v-for 的变量名");
                if (ExprParser.Host.is(n)) throw syntax(vFor.line, vFor.col, "'" + n + "' 是宿主注入的只读名字，不能当 v-for 的变量名");
                if (scope.isState(n)) throw SfcError.at(Code.E_TPL_SHADOW, vFor.line, vFor.col, n);
                if (scope.isLoopVar(n)) throw syntax(vFor.line, vFor.col, "v-for 的变量 '" + n + "' 和外层 v-for 的同名，换个名字");
            }
            if (item.equals(index)) throw syntax(vFor.line, vFor.col, "v-for 的两个变量同名");
            Decoded decodedFor = decodeMapped(vFor.raw);
            int rawStart = decodedFor.rawIndex()[m.end()];
            int[] at = walk(vFor.raw, vFor.valueLine, vFor.valueCol, rawStart);
            Typed source = compileExpr(vFor.raw.substring(rawStart), scope, at[0], at[1]);
            if (source.type() != T.ARR && source.type() != T.INT && source.type() != T.ANY) {
                throw syntax(at[0], at[1], "v-for 的来源要是数组或整数，这里是 " + source.type().label);
            }
            T itemType = source.type() == T.INT ? T.INT : source.type() == T.ARR ? source.elem() : T.ANY;
            inner = scope.with(item, itemType, T.ANY);
            if (index != null) inner = inner.with(index, T.INT, T.ANY);
            forSpec = new ForSpec(item, index, new Expr.Compiled(source.expr(), at[0] + lineOffset, vFor.raw.substring(rawStart)));
        }

        Expr.Compiled perItemIf = vFor != null && vIf != null ? expr(vIf, inner) : null;

        String idValue = null;
        if (id != null) {
            if (id.value == null || !ID.matcher(id.value).matches()) {
                throw badValue(tag, id, "匹配 [a-z][a-z0-9_-]{0,31}");
            }
            idValue = id.value;
        }
        List<String> classes = new ArrayList<>();
        if (cls != null) {
            if (cls.value == null) throw badValue(tag, cls, "空格分隔的 class 名");
            for (String c : Java8.strip(cls.value).split("\\s+")) {
                if (c.isEmpty()) continue;
                if (!CLASS_NAME.matcher(c).matches()) throw badValue(tag, cls, "每个匹配 [a-z][a-z0-9_-]{0,31}");
                classes.add(c);
            }
            if (classes.size() > NodeParser.MAX_CLASSES) throw badValue(tag, cls, "最多 " + NodeParser.MAX_CLASSES + " 个 class");
        }

        Map<String, Object> staticProps = new LinkedHashMap<>();
        // Keep editing focus tied to this template element when conditional siblings change paths.
        if (type.textEditor()) staticProps.put("input-site", raw.line + ":" + raw.col);
        for (Attr a : statics) {
            PropRules.Spec spec = specs.get(a.name);
            if (spec.numberOrBool() && a.value != null && (inner.isState(a.value) || inner.isLoopVar(a.value))) {
                throw SfcError.at(Code.E_TPL_LIKELY_MISSING_COLON, a.line, a.col, a.name, a.value, a.name, a.value);
            }
            if (spec.staticOnly()) {
                staticProps.put(a.name, bindKey(tag, a, spec, inner));
                continue;
            }
            try {
                staticProps.put(a.name, PropRules.parseStatic(spec, a.value));
            } catch (PropRules.Bad bad) {
                throw badValue(tag, a, bad.getMessage());
            }
        }
        Map<String, BoundProp> boundProps = new LinkedHashMap<>();
        for (Attr a : bounds) {
            String name = a.name.substring(1);
            boundProps.put(name, new BoundProp(specs.get(name), expr(a, inner)));
        }

        Expr.Compiled keyExpr = key != null ? expr(key, inner) : null;
        Statements statements = null;
        if (click != null) statements = statements(click, inner);
        Statements inputStatements = input == null ? null : statements(input, inner);

        // 子内容
        boolean hasElementChild = false;
        boolean hasText = false;
        for (Raw r : raw.children) {
            if (r instanceof RawElement) hasElementChild = true;
            if (r instanceof RawText && !((RawText) r).blank()) hasText = true;
        }
        List<Child> children = new ArrayList<>();
        if (!type.acceptsChildren && type != NodeType.TEXT) {
            if (!raw.selfClosed) throw SfcError.at(Code.E_TPL_MUST_SELF_CLOSE, raw.line, raw.col, tag, tag);
        } else if (type == NodeType.TEXT || (type == NodeType.BUTTON && hasText && !hasElementChild)) {
            if (hasElementChild) throw SfcError.at(Code.E_TPL_MUST_SELF_CLOSE, raw.line, raw.col, tag, tag);
            if (hasText) {
                if (staticProps.containsKey("text") || boundProps.containsKey("text")) {
                    throw syntax(raw.line, raw.col, "<" + tag + "> 的文字内容和 text 属性只能写一个");
                }
                Text content = content(raw.children, inner, tag, "text");
                if (content.literal() != null) {
                    staticProps.put("text", content.literal());
                } else {
                    boundProps.put("text", new BoundProp(specs.get("text"), content.expr()));
                }
            } else if (type == NodeType.TEXT && !raw.selfClosed && !staticProps.containsKey("text")
                    && !boundProps.containsKey("text") && !staticProps.containsKey("i18n") && !boundProps.containsKey("i18n")) {
                staticProps.put("text", "");
            }
        } else {
            children = children(raw, inner, forDepth + (vFor != null ? 1 : 0), depth);
        }

        requireProps(raw, staticProps, boundProps, hasElementChild);
        // IR 里 button 的 onClick 必填（§5.3），两种形态判得一样；排在文字必填之后，与 IR 的报错顺序一致（§4.8 的 5f 在 5h 之前）
        if (type == NodeType.BUTTON && click == null) {
            throw SfcError.at(Code.E_TPL_BAD_VALUE, raw.line, raw.col, tag, "@click", "（没写）",
                    "必填：按钮要写点了做什么，例如 @click=\"close()\"");
        }

        return new Element(type, raw.line + lineOffset, idValue, ImmutableList.copyOf(classes), Collections.unmodifiableMap(staticProps),
                Collections.unmodifiableMap(boundProps), forSpec, perItemIf, keyExpr, statements, inputStatements, ImmutableList.copyOf(children));
    }

    /** 兄弟节点：把 v-if / v-else-if / v-else 收成一串，文字各成一个 text 节点。 */
    private List<Child> children(RawElement parent, Scope scope, int forDepth, int depth) {
        List<Child> out = new ArrayList<>();
        List<Expr.Compiled> conds = null;
        List<Element> branches = null;
        boolean closed = true;
        for (Raw r : parent.children) {
            if (r instanceof RawComment) continue;
            if (r instanceof RawText) {
                if (((RawText) r).blank()) continue;
                closed = flush(out, conds, branches);
                conds = null;
                branches = null;
                out.add(content(ImmutableList.of(((RawText) r)), scope, parent.name, "文字内容"));
                continue;
            }
            RawElement e = (RawElement) r;
            Attr vIf = attr(e, "v-if");
            Attr vElseIf = attr(e, "v-else-if");
            Attr vElse = attr(e, "v-else");
            if (vElseIf != null || vElse != null) {
                if (conds == null || closed) {
                    Attr a = vElseIf != null ? vElseIf : vElse;
                    throw SfcError.at(Code.E_TPL_DANGLING_ELSE, a.line, a.col);
                }
                conds.add(vElseIf != null ? expr(vElseIf, scope) : null);
                branches.add(element(e, scope, forDepth, depth + 1));
                closed = vElse != null;
                continue;
            }
            flush(out, conds, branches);
            conds = null;
            branches = null;
            if (vIf != null && attr(e, "v-for") == null) {
                conds = new ArrayList<>();
                branches = new ArrayList<>();
                conds.add(expr(vIf, scope));
                branches.add(element(e, scope, forDepth, depth + 1));
                closed = false;
            } else {
                out.add(element(e, scope, forDepth, depth + 1));
                closed = true;
            }
        }
        flush(out, conds, branches);
        return out;
    }

    private static boolean flush(List<Child> out, List<Expr.Compiled> conds, List<Element> branches) {
        if (conds != null) out.add(new Chain(Collections.unmodifiableList(new ArrayList<>(conds)), ImmutableList.copyOf(branches)));
        return true;
    }

    /**
     * 文字内容 → 字面文字或拼接表达式；正文 ≤4096，按钮自身文字仍 ≤512，除换行外没有控制字符。
     * 连续 ASCII 空白压成一个空格、去掉首尾，和 Vue 的 condense 一样；在原文上压完才解码实体，否则 &#10; 解出的换行和 &nbsp; 会被一起压掉。
     */
    private Text content(List<Raw> raws, Scope scope, String owner, String what) {
        List<Piece> pieces = new ArrayList<>();
        for (Raw r : raws) {
            if (r instanceof RawText) pieces.addAll(((RawText) r).pieces);
        }
        List<Object> parts = new ArrayList<>();
        for (Piece p : pieces) {
            if (p.literal != null) {
                String s = ASCII_SPACE.matcher(p.literal).replaceAll(" ");
                Object prevMatch;
                if (!parts.isEmpty() && (prevMatch = parts.get(parts.size() - 1)) instanceof String) {
                    parts.set(parts.size() - 1, ((String) prevMatch) + s);
                } else {
                    parts.add(s);
                }
            } else {
                parts.add(compileExpr(p.expr, scope, p.line, p.col));
            }
        }
        Object sMatch;
        if (!parts.isEmpty() && (sMatch = parts.get(0)) instanceof String && ((String) sMatch).startsWith(" ")) parts.set(0, ((String) sMatch).substring(1));
        int last = parts.size() - 1;
        Object sMatch_;
        if (last >= 0 && (sMatch_ = parts.get(last)) instanceof String && ((String) sMatch_).endsWith(" ")) parts.set(last, ((String) sMatch_).substring(0, ((String) sMatch_).length() - 1));
        parts.removeIf(p -> p instanceof String && ((String) p).isEmpty());
        parts.replaceAll(p -> p instanceof String ? decode((String) p) : p);

        int[] at = start(pieces);
        StringBuilder literal = new StringBuilder();
        boolean allLiteral = true;
        for (Object p : parts) {
            if (p instanceof String) {
                literal.append(((String) p));
            } else {
                allLiteral = false;
            }
        }
        int maxText = owner.equals("button") ? NodeParser.MAX_TEXT : StateRules.MAX_PAGE_STRING;
        if (literal.length() > maxText || !PropRules.visible(literal.toString())) {
            throw SfcError.at(Code.E_TPL_BAD_VALUE, at[0], at[1], owner, what, shorten(literal.toString()),
                    "最多 " + maxText + " 字，除换行外没有控制字符");
        }
        if (allLiteral) return new Text(at[0] + lineOffset, literal.toString(), null);

        List<Expr> exprs = new ArrayList<>();
        for (Object p : parts) exprs.add(p instanceof String ? new Expr.Lit(((String) p)) : ((Typed) p).expr());
        int exprLine = at[0];
        for (Piece p : pieces) {
            if (p.expr != null) {
                exprLine = p.line;
                break;
            }
        }
        return new Text(at[0] + lineOffset, null, new Expr.Compiled(new Expr.Concat(ImmutableList.copyOf(exprs)), exprLine + lineOffset, "{{ }}"));
    }

    /** 文字真正开始的位置：第一个非空白字符或第一个 {{。报错指到这里，而不是前一个标签的 >。 */
    private static int[] start(List<Piece> pieces) {
        for (Piece p : pieces) {
            if (p.expr != null) return new int[]{p.line, Math.max(1, p.col - 2)};
            for (int i = 0; i < p.literal.length(); i++) {
                char ch = p.literal.charAt(i);
                if (ch != ' ' && ch != '\t' && ch != '\n' && ch != '\r' && ch != '\f') return walk(p.literal, p.line, p.col, i);
            }
        }
        return pieces.isEmpty() ? new int[]{1, 1} : new int[]{pieces.get(0).line, pieces.get(0).col};
    }

    private void requireProps(RawElement raw, Map<String, Object> statics, Map<String, BoundProp> bounds,
                              boolean hasElementChild) {
        String tag = raw.name;
        boolean hasText = statics.containsKey("text") || bounds.containsKey("text");
        boolean hasI18n = statics.containsKey("i18n") || bounds.containsKey("i18n");
        if (hasText && hasI18n) {
            throw SfcError.at(Code.E_TPL_BAD_VALUE, raw.line, raw.col, tag, "i18n", "text 与 i18n 都写了", "二选一");
        }
        if ((statics.containsKey("args") || bounds.containsKey("args")) && !hasI18n) {
            throw SfcError.at(Code.E_TPL_BAD_VALUE, raw.line, raw.col, tag, "args", "有 args 没有 i18n", "args 仅在写了 i18n 时有效");
        }
        for (List<String> group : PropRules.required(raw.type)) {
            boolean present = group.stream().anyMatch(n -> statics.containsKey(n) || bounds.containsKey(n));
            if (raw.type == NodeType.BUTTON && hasElementChild) present = true;
            if (!present) {
                throw SfcError.at(Code.E_TPL_BAD_VALUE, raw.line, raw.col, tag, String.join(" / ", group), "（没写）",
                        "必填" + (raw.type == NodeType.TEXT || raw.type == NodeType.BUTTON ? "，也可以写成 <" + tag + ">文字</" + tag + ">" : ""));
            }
        }
    }

    /** bind 的值是 state 的 key 名，而且种类要对得上（toggle 要 bool，tab-bar 要 int）。 */
    private static String bindKey(String tag, Attr a, PropRules.Spec spec, Scope scope) {
        if (a.value == null || !scope.isState(a.value)) {
            ExprParser.Tok at = new ExprParser.Tok('n', String.valueOf(a.value), null, a.valueLine, a.valueCol, 0);
            throw ExprParser.unknownIdent(at, stateNames(scope));
        }
        String want;
        switch (spec.kind()) {
            case BIND_BOOL:
                want = "bool";
                break;
            case BIND_STRING:
                want = "string";
                break;
            default:
                want = "int";
                break;
        }
        String actual = StateRules.kind(scope.initial(a.value));
        if (!want.equals(actual)) {
            throw SfcError.at(Code.E_TPL_BAD_VALUE, a.line, a.col, tag, a.name, a.value, spec.allowed() + "，'" + a.value + "' 是 " + actual);
        }
        return a.value;
    }

    private static List<String> stateNames(Scope scope) {
        List<String> out = new ArrayList<>();
        for (String n : scope.names()) {
            if (scope.isState(n)) out.add(n);
        }
        return out;
    }

    private Expr.Compiled expr(Attr a, Scope scope) {
        Typed t = compileExpr(a.raw, scope, a.valueLine, a.valueCol);
        return new Expr.Compiled(t.expr(), a.valueLine + lineOffset, a.raw);
    }

    private Statements statements(Attr a, Scope scope) {
        Decoded d = decodeMapped(a.raw);
        try {
            return (a.name.equals("@input")
                    ? Statements.parseInput(d.text(), scope, a.valueLine, a.valueCol, script)
                    : Statements.parse(d.text(), scope, a.valueLine, a.valueCol, script)).shifted(lineOffset);
        } catch (SfcError e) {
            throw remap(e, d, a.raw, a.valueLine, a.valueCol);
        }
    }

    /** 模板里的一条表达式：256 按原文量（§9.5.7 限的是源码长度），解码实体后再解析，报错位置换回原文。 */
    private static Typed compileExpr(String raw, Scope scope, int line, int col) {
        if (raw.length() > ExprParser.MAX_LENGTH) throw SfcError.at(Code.E_EXPR_TOO_LONG, line, col);
        Decoded d = decodeMapped(raw);
        try {
            return ExprParser.expression(d.text(), scope, line, col);
        } catch (SfcError e) {
            throw remap(e, d, raw, line, col);
        }
    }

    /** 解码后的串上报的行列 → 原文上的行列。&amp; 比 & 长 4 个字符，不换算的话它后面的列全都偏。 */
    private static SfcError remap(SfcError e, Decoded d, String raw, int line, int col) {
        if (d.text().equals(raw)) return e;
        int offset = offsetAt(d.text(), line, col, e.line(), e.col());
        if (offset < 0) return e;
        int[] at = walk(raw, line, col, d.rawIndex()[offset]);
        return e.movedTo(at[0], at[1]);
    }

    /** s 从 (line, col) 开始，(targetLine, targetCol) 是第几个字符；不在 s 里返回 -1。 */
    private static int offsetAt(String s, int line, int col, int targetLine, int targetCol) {
        int l = line;
        int c = col;
        for (int i = 0; i <= s.length(); i++) {
            if (l == targetLine && c == targetCol) return i;
            if (i == s.length()) break;
            if (s.charAt(i) == '\n') {
                l++;
                c = 1;
            } else {
                c++;
            }
        }
        return -1;
    }

    /** s 从 (line, col) 开始，第 offset 个字符的行列：值可以跨行。 */
    private static int[] walk(String s, int line, int col, int offset) {
        int l = line;
        int c = col;
        for (int i = 0; i < offset && i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                l++;
                c = 1;
            } else {
                c++;
            }
        }
        return new int[]{l, c};
    }

    private static Attr attr(RawElement e, String name) {
        for (Attr a : e.attrs) {
            if (a.name.equals(name)) return a;
        }
        return null;
    }

    private static SfcError unknownAttr(RawElement raw, Attr a, Map<String, PropRules.Spec> specs) {
        List<String> accepted = new ArrayList<>(ImmutableList.of("id", "class", "v-if", "v-else-if", "v-else", "v-for", ":key"));
        for (PropRules.Spec s : specs.values()) {
            if (!s.boundOnly()) accepted.add(s.name());
            if (!s.staticOnly()) accepted.add(":" + s.name());
        }
        if (CLICKABLE.contains(raw.type)) accepted.add("@click");
        if (raw.type.textEditor()) accepted.add("@input");
        return SfcError.at(Code.E_TPL_UNKNOWN_ATTR, a.line, a.col, raw.name, a.name, Names.hint(a.name, accepted),
                String.join(" ", accepted));
    }

    private static SfcError badValue(String tag, Attr a, String allowed) {
        return SfcError.at(Code.E_TPL_BAD_VALUE, a.line, a.col, tag, a.name, a.value == null ? "（没写值）" : shorten(a.value), allowed);
    }

    private static SfcError syntax(int line, int col, String reason) {
        return SfcError.at(Code.E_TPL_SYNTAX, line, col, reason);
    }

    /** 放进报错文案的值：截到 40 字，控制字符写成 U+XXXX，否则文案里看不见错在哪。 */
    private static String shorten(String s) {
        String cut = s.length() > 40 ? Values.cut(s, 40) : s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cut.length(); i++) {
            char ch = cut.charAt(i);
            if (ch < 0x20 || ch == 0x7F) {
                sb.append(ExprParser.shown(ch));
            } else {
                sb.append(ch);
            }
        }
        return cut.length() < s.length() ? sb + "…" : sb.toString();
    }

    /** 解码后的串与原文的对照：rawIndex[i] 是解码后第 i 个字符在原文里的下标，末尾多一格是原文长度。 */
    private static final class Decoded {
        private final String text;
        private final int[] rawIndex;

        private Decoded(String text, int[] rawIndex) {
            this.text = text;
            this.rawIndex = rawIndex;
        }

        public String text() { return text; }

        public int[] rawIndex() { return rawIndex; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Decoded)) return false;
            Decoded that = (Decoded) other;
            return java.util.Objects.equals(this.text, that.text)
                    && java.util.Objects.equals(this.rawIndex, that.rawIndex);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(text);
            hash = 31 * hash + java.util.Objects.hashCode(rawIndex);
            return hash;
        }

        @Override public String toString() { return "Decoded[text=" + text + ", rawIndex=" + rawIndex + "]"; }

    }

    /** HTML 实体：五个常用的、&nbsp; 与数字形式。认不出的原样留着。 */
    static String decode(String s) {
        return s.indexOf('&') < 0 ? s : decodeMapped(s).text();
    }

    private static Decoded decodeMapped(String s) {
        StringBuilder sb = new StringBuilder();
        int[] index = new int[s.length() + 1];
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            int semi = c == '&' ? s.indexOf(';', i) : -1;
            if (semi > i + 1 && semi - i <= 10) {
                String name = s.substring(i + 1, semi);
                String out;
                switch (name) {
                    case "lt":
                        out = "<";
                        break;
                    case "gt":
                        out = ">";
                        break;
                    case "amp":
                        out = "&";
                        break;
                    case "quot":
                        out = "\"";
                        break;
                    case "apos":
                    case "#39":
                        out = "'";
                        break;
                    case "nbsp":
                        out = String.valueOf((char) 0xA0);
                        break;
                    default:
                        out = numeric(name);
                        break;
                }
                if (out != null) {
                    for (int k = 0; k < out.length(); k++) index[sb.length() + k] = i;
                    sb.append(out);
                    i = semi + 1;
                    continue;
                }
            }
            index[sb.length()] = i;
            sb.append(c);
            i++;
        }
        index[sb.length()] = s.length();
        return new Decoded(sb.toString(), java.util.Arrays.copyOf(index, sb.length() + 1));
    }

    private static String numeric(String name) {
        try {
            int cp;
            if (name.startsWith("#x") || name.startsWith("#X")) {
                cp = Integer.parseInt(name.substring(2), 16);
            } else if (name.startsWith("#")) {
                cp = Integer.parseInt(name.substring(1));
            } else {
                return null;
            }
            return Character.isValidCodePoint(cp) && !Character.isSurrogate((char) cp) || cp > 0xFFFF && Character.isValidCodePoint(cp)
                    ? new String(Character.toChars(cp)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
