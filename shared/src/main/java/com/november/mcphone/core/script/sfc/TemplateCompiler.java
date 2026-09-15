package com.november.mcphone.core.script.sfc;

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
    private static final Set<String> KEYWORDS = Set.of("true", "false", "null", "in");
    /** Vue 的 condense 只压 ASCII 空白；全角空格、&nbsp; 与 &#10; 解出来的换行都是作者有意写的。 */
    private static final Pattern ASCII_SPACE = Pattern.compile("[ \\t\\n\\r\\f]+");
    private static final Set<NodeType> CLICKABLE = Set.of(NodeType.BUTTON, NodeType.TOGGLE, NodeType.TAB_BAR);
    private static final List<String> ELEMENT_NAMES = Arrays.stream(NodeType.values()).map(t -> t.json).toList();

    // ============================================================
    //  原始树
    // ============================================================

    record Attr(String name, String value, int line, int col, int valueLine, int valueCol) {
    }

    /** 文字里的一段：literal 不为 null 是字面文字，否则是 {{ }} 里的表达式源码。 */
    record Piece(String literal, String expr, int line, int col) {
    }

    sealed interface Raw permits RawElement, RawText, RawComment {
    }

    record RawElement(String name, NodeType type, int line, int col, List<Attr> attrs, boolean selfClosed,
                      List<Raw> children) implements Raw {
    }

    record RawText(int line, List<Piece> pieces) implements Raw {
        /** 只有 ASCII 空白才算空：全角空格是作者写的内容。 */
        boolean blank() {
            for (Piece p : pieces) {
                if (p.expr != null || !ASCII_SPACE.matcher(p.literal).replaceAll("").isEmpty()) return false;
            }
            return true;
        }
    }

    record RawComment() implements Raw {
    }

    private final String src;
    private int pos;
    private int line = 1;
    private int col = 1;
    private int elements;

    private TemplateCompiler(String src) {
        this.src = src;
    }

    /** 编译一个 {@code <template>} 块的内容。state 是 {@code <script>} 给的初值，决定哪些名字声明过。 */
    public static CompiledTemplate compile(String content, Map<String, Object> state) {
        Objects.requireNonNull(content, "content");
        TemplateCompiler c = new TemplateCompiler(content);
        RawElement root = c.document();
        Map<String, Object> declared = Collections.unmodifiableMap(new LinkedHashMap<>(state));
        Element compiled = c.element(root, Scope.of(declared), 0, 1);
        return new CompiledTemplate(compiled, declared);
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
        return new RawElement(name, type, tagLine, tagCol, List.copyOf(attrs), selfClosed, List.copyOf(children));
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
            return new Attr(name, null, aLine, aCol, aLine, aCol);
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
            String value = decode(src.substring(vStart, pos));
            advance();
            return new Attr(name, value, aLine, aCol, vLine, vCol);
        }
        int vLine = line;
        int vCol = col;
        int vStart = pos;
        while (pos < src.length() && !Character.isWhitespace(src.charAt(pos)) && src.charAt(pos) != '>'
                && !src.startsWith("/>", pos)) {
            advance();
        }
        return new Attr(name, decode(src.substring(vStart, pos)), aLine, aCol, vLine, vCol);
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
        String name = src.substring(start, pos).strip();
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
        Attr id = null;
        Attr cls = null;
        List<Attr> statics = new ArrayList<>();
        List<Attr> bounds = new ArrayList<>();
        for (Attr a : raw.attrs) {
            switch (a.name) {
                case "v-if" -> vIf = a;
                case "v-else-if" -> vElseIf = a;
                case "v-else" -> vElse = a;
                case "v-for" -> vFor = a;
                case ":key" -> key = a;
                case "id" -> id = a;
                case "class" -> cls = a;
                default -> {
                    if (a.name.startsWith("@")) {
                        if (!CLICKABLE.contains(type) && a.name.equals("@click")) {
                            throw SfcError.at(Code.E_TPL_EVENT_NOT_ALLOWED, a.line, a.col, tag);
                        }
                        if (!a.name.equals("@click")) throw unknownAttr(raw, a, specs);
                        click = a;
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
            }
        }

        int directives = (vIf != null ? 1 : 0) + (vElseIf != null ? 1 : 0) + (vElse != null ? 1 : 0);
        if (directives > 1) throw syntax(raw.line, raw.col, "v-if / v-else-if / v-else 一个元素只能写一个");
        if (vFor != null && (vElse != null || vElseIf != null)) {
            throw syntax(vFor.line, vFor.col, "v-for 不能和 v-else / v-else-if 写在同一个元素上");
        }
        for (Attr a : new Attr[]{vIf, vElseIf, vFor, key, click}) {
            if (a != null && (a.value == null || a.value.isBlank())) throw syntax(a.line, a.col, a.name + " 要写值");
        }
        if (vElse != null && vElse.value != null) throw syntax(vElse.line, vElse.col, "v-else 不写值");
        for (Attr b : bounds) {
            if (b.value == null || b.value.isBlank()) throw syntax(b.line, b.col, b.name + " 要写表达式");
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
            for (String n : index == null ? List.of(item) : List.of(item, index)) {
                if (KEYWORDS.contains(n)) throw syntax(vFor.line, vFor.col, "'" + n + "' 不能当 v-for 的变量名");
                if (scope.isState(n)) throw SfcError.at(Code.E_TPL_SHADOW, vFor.line, vFor.col, n);
                if (scope.isLoopVar(n)) throw syntax(vFor.line, vFor.col, "v-for 的变量 '" + n + "' 和外层 v-for 的同名，换个名字");
            }
            if (item.equals(index)) throw syntax(vFor.line, vFor.col, "v-for 的两个变量同名");
            int[] at = position(vFor, m.end());
            Typed source = ExprParser.expression(vFor.value.substring(m.end()), scope, at[0], at[1]);
            if (source.type() != T.ARR && source.type() != T.INT && source.type() != T.ANY) {
                throw syntax(at[0], at[1], "v-for 的来源要是数组或整数，这里是 " + source.type().label);
            }
            T itemType = source.type() == T.INT ? T.INT : source.type() == T.ARR ? source.elem() : T.ANY;
            inner = scope.with(item, itemType, T.ANY);
            if (index != null) inner = inner.with(index, T.INT, T.ANY);
            forSpec = new ForSpec(item, index, compiled(source, vFor, m.end()));
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
            for (String c : cls.value.strip().split("\\s+")) {
                if (c.isEmpty()) continue;
                if (!CLASS_NAME.matcher(c).matches()) throw badValue(tag, cls, "每个匹配 [a-z][a-z0-9_-]{0,31}");
                classes.add(c);
            }
            if (classes.size() > NodeParser.MAX_CLASSES) throw badValue(tag, cls, "最多 " + NodeParser.MAX_CLASSES + " 个 class");
        }

        Map<String, Object> staticProps = new LinkedHashMap<>();
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
        if (click != null) statements = Statements.parse(click.value, inner, click.valueLine, click.valueCol);
        // IR 里 button 的 onClick 必填（§5.3），两种形态判得一样
        if (type == NodeType.BUTTON && click == null) {
            throw SfcError.at(Code.E_TPL_BAD_VALUE, raw.line, raw.col, tag, "@click", "（没写）",
                    "必填：按钮要写点了做什么，例如 @click=\"close()\"");
        }

        // 子内容
        boolean hasElementChild = false;
        boolean hasText = false;
        for (Raw r : raw.children) {
            if (r instanceof RawElement) hasElementChild = true;
            if (r instanceof RawText t && !t.blank()) hasText = true;
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
                Text content = content(raw.children, inner);
                if (content.literal() != null) {
                    staticProps.put("text", staticText(raw.line, raw.col, content.literal()));
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

        return new Element(type, raw.line, idValue, List.copyOf(classes), Collections.unmodifiableMap(staticProps),
                Collections.unmodifiableMap(boundProps), forSpec, perItemIf, keyExpr, statements, List.copyOf(children));
    }

    /** 兄弟节点：把 v-if / v-else-if / v-else 收成一串，文字各成一个 text 节点。 */
    private List<Child> children(RawElement parent, Scope scope, int forDepth, int depth) {
        List<Child> out = new ArrayList<>();
        List<Expr.Compiled> conds = null;
        List<Element> branches = null;
        boolean closed = true;
        for (Raw r : parent.children) {
            if (r instanceof RawComment) continue;
            if (r instanceof RawText t) {
                if (t.blank()) continue;
                closed = flush(out, conds, branches);
                conds = null;
                branches = null;
                Text text = content(List.of(t), scope);
                if (text.literal() != null) staticText(t.line, 1, text.literal());
                out.add(text);
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
        if (conds != null) out.add(new Chain(Collections.unmodifiableList(new ArrayList<>(conds)), List.copyOf(branches)));
        return true;
    }

    /** 文字内容 → 字面文字或拼接表达式。连续空白压成一个空格，首尾去掉，和 Vue 的 condense 一样。 */
    private Text content(List<Raw> raws, Scope scope) {
        List<Piece> pieces = new ArrayList<>();
        int firstLine = -1;
        for (Raw r : raws) {
            if (r instanceof RawText t) {
                if (firstLine < 0) firstLine = t.line;
                pieces.addAll(t.pieces);
            }
        }
        // 先在原文上压空白、去首尾，最后才解码实体：反过来的话 &#10; 解出的换行和 &nbsp; 会被一起压掉
        List<Object> parts = new ArrayList<>();
        for (Piece p : pieces) {
            if (p.literal != null) {
                String s = ASCII_SPACE.matcher(p.literal).replaceAll(" ");
                if (!parts.isEmpty() && parts.get(parts.size() - 1) instanceof String prev) {
                    parts.set(parts.size() - 1, prev + s);
                } else {
                    parts.add(s);
                }
            } else {
                parts.add(ExprParser.expression(p.expr, scope, p.line, p.col));
            }
        }
        if (!parts.isEmpty() && parts.get(0) instanceof String s && s.startsWith(" ")) parts.set(0, s.substring(1));
        int last = parts.size() - 1;
        if (last >= 0 && parts.get(last) instanceof String s && s.endsWith(" ")) parts.set(last, s.substring(0, s.length() - 1));
        parts.removeIf(p -> p instanceof String s && s.isEmpty());
        parts.replaceAll(p -> p instanceof String s ? decode(s) : p);

        int line = firstLine < 0 ? 1 : firstLine;
        if (parts.stream().allMatch(p -> p instanceof String)) return new Text(line, String.join("", parts.stream().map(p -> (String) p).toList()), null);
        List<Expr> exprs = new ArrayList<>();
        int exprLine = line;
        for (Object p : parts) {
            if (p instanceof String s) {
                exprs.add(new Expr.Lit(s));
            } else {
                exprs.add(((Typed) p).expr());
            }
        }
        for (Piece p : pieces) {
            if (p.expr != null) {
                exprLine = p.line;
                break;
            }
        }
        return new Text(line, null, new Expr.Compiled(new Expr.Concat(List.copyOf(exprs)), exprLine, "{{ }}"));
    }

    /** 编进 text 节点的字面文字：与 IR 的 text 同一条判据（§5.2）。 */
    private static String staticText(int line, int col, String text) {
        if (text.length() > NodeParser.MAX_TEXT || !PropRules.visible(text)) {
            throw SfcError.at(Code.E_TPL_BAD_VALUE, line, col, "text", "text", shorten(text),
                    "最多 " + NodeParser.MAX_TEXT + " 字，除换行外没有控制字符");
        }
        return text;
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
        String want = spec.kind() == PropRules.Kind.BIND_BOOL ? "bool" : "int";
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

    private static Expr.Compiled expr(Attr a, Scope scope) {
        Typed t = ExprParser.expression(a.value, scope, a.valueLine, a.valueCol);
        return new Expr.Compiled(t.expr(), a.valueLine, a.value);
    }

    private Expr.Compiled compiled(Typed t, Attr a, int offset) {
        return new Expr.Compiled(t.expr(), position(a, offset)[0], a.value.substring(offset));
    }

    /** 属性值里第 offset 个字符的行列：值可以跨行。 */
    private static int[] position(Attr a, int offset) {
        int l = a.valueLine;
        int c = a.valueCol;
        for (int i = 0; i < offset; i++) {
            if (a.value.charAt(i) == '\n') {
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
        List<String> accepted = new ArrayList<>(List.of("id", "class", "v-if", "v-else-if", "v-else", "v-for", ":key"));
        for (PropRules.Spec s : specs.values()) {
            if (!s.boundOnly()) accepted.add(s.name());
            if (!s.staticOnly()) accepted.add(":" + s.name());
        }
        if (CLICKABLE.contains(raw.type)) accepted.add("@click");
        return SfcError.at(Code.E_TPL_UNKNOWN_ATTR, a.line, a.col, raw.name, a.name, Names.hint(a.name, accepted),
                String.join(" ", accepted));
    }

    private static SfcError badValue(String tag, Attr a, String allowed) {
        return SfcError.at(Code.E_TPL_BAD_VALUE, a.line, a.col, tag, a.name, a.value == null ? "（没写值）" : shorten(a.value), allowed);
    }

    private static SfcError syntax(int line, int col, String reason) {
        return SfcError.at(Code.E_TPL_SYNTAX, line, col, reason);
    }

    private static String shorten(String s) {
        return s.length() > 40 ? Values.cut(s, 40) + "…" : s;
    }

    /** HTML 实体：五个常用的与数字形式。认不出的原样留着。 */
    static String decode(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            int semi = c == '&' ? s.indexOf(';', i) : -1;
            if (semi > i + 1 && semi - i <= 10) {
                String name = s.substring(i + 1, semi);
                String out = switch (name) {
                    case "lt" -> "<";
                    case "gt" -> ">";
                    case "amp" -> "&";
                    case "quot" -> "\"";
                    case "apos", "#39" -> "'";
                    case "nbsp" -> String.valueOf((char) 0xA0);
                    default -> numeric(name);
                };
                if (out != null) {
                    sb.append(out);
                    i = semi + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
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
