package com.november.mcphone.core.script.sfc;

import com.november.mcphone.core.script.layout.MssError;
import com.november.mcphone.core.script.layout.Node;
import com.november.mcphone.core.script.layout.NodeParser;
import com.november.mcphone.core.script.layout.NodeType;
import com.november.mcphone.core.script.layout.ScriptNodeTest;
import com.november.mcphone.core.script.layout.UiState;
import com.november.mcphone.core.script.pkg.Manifest;
import com.november.mcphone.core.script.pkg.PackageError;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * .vue 单文件编译器的断言测试（施工方案 §9.12 的骨架在 {@link #main} 开头，只加不减），用 javac 单独编，不需要 Minecraft。
 *
 * <p>骨架里 {@code ev("1 + 2 * 3")} 期望 9 是笔误：§9.5.1 乘先于加，结果是 7（旁边的注释「不是 (1+2)*3」也这么说）。
 */
public class SfcCompilerTest {

    static int checks = 0;
    static int rejections = 0;
    static final List<String> failures = new ArrayList<>();
    static final Set<String> triggered = new TreeSet<>();

    static void eq(Object actual, Object expected, String what) {
        checks++;
        if (!java.util.Objects.equals(actual, expected)) {
            failures.add(what + "  期望 " + expected + "，实际 " + actual);
        }
    }

    static void check(boolean cond, String what) {
        checks++;
        if (!cond) failures.add(what);
    }

    static void report() {
        System.out.println();
        check(rejections >= 20, "拒绝项不少于 20 条，实际 " + rejections);
        check(checks >= 60, "断言不少于 60 条，实际 " + checks);
        if (failures.isEmpty()) {
            System.out.println("全部通过：" + checks + " 条断言，其中拒绝项 " + rejections + " 条");
        } else {
            System.out.println("失败 " + failures.size() + " / " + checks + " 条：");
            for (String f : failures) System.out.println("  ✗ " + f);
            System.exit(1);
        }
    }

    public static void main(String[] a) throws Exception {
        // ---- 块切分 ----
        var b = split("<manifest>\n{}\n</manifest>\n<template>\n<box/>\n</template>\n");
        eq(b.get("manifest").startLine(), 2, "manifest 内容起始行");
        eq(b.get("template").startLine(), 5, "template 内容起始行");

        rejects("  <template>\n</template>",        "E_SFC_BLOCK_FORMAT");
        rejects("<template></template>",            "E_SFC_BLOCK_FORMAT");
        rejects("<tmpl>\n</tmpl>",                  "E_SFC_UNKNOWN_BLOCK");
        rejects("<template>\n</template>\n<template>\n</template>", "E_SFC_DUP_BLOCK");
        rejects("<template>\n<style>\n</style>\n</template>",       "E_SFC_NESTED_BLOCK");
        rejects("<template>\n",                     "E_SFC_UNCLOSED_BLOCK");
        rejects("垃圾\n<template>\n</template>",     "E_SFC_STRAY_TEXT");
        // JS 里的 </script> 只要不顶第 0 列就没事
        accepts("<script>\n  state = { s: \" </script> \" }\n</script>\n" + MIN_TPL);

        // ---- 行号映射：错误必须报原始文件行 ----
        eq(errorOf("""
<manifest>
{"format":1,"id":"a:b","version":"1.0.0","name":"n","author":"y"}
</manifest>
<style>
.a { color: #fff; }
</style>
<template>
<box/>
</template>
""").line(), 5, "style 里的错误报原始第 5 行，不是块内第 1 行");

        eq(errorOf(withTemplateAtLine(9, "<text>{{ conut }}</text>")).line(), 9,
           "template 里的错误报原始行号");

        // ---- 模板编译 ----
        Node n = compile("<column><text>hi</text></column>");
        eq(n.type(), NodeType.COLUMN, "元素名映射到 NodeType");
        eq(n.children().size(), 1, "子元素");
        eq(n.children().get(0).str("text", null), "hi", "纯文本子内容变成 text 的 text 属性");

        // 插值拼接
        eq(inst("<text>点了 {{ count }} 次</text>", state("count", 3)).str("text", null),
           "点了 3 次", "插值与字面量拼接");

        // v-if 假分支完全不进树
        eq(inst("<column><box v-if=\"f\"/><box v-else/></column>", state("f", false))
             .children().size(), 1, "v-if/v-else 只留一个分支");

        // v-for 展开成兄弟节点
        eq(inst("<column><text v-for=\"x in items\" :text=\"x\"/></column>",
                state("items", List.of("a","b","c"))).children().size(), 3, "v-for 展开 3 个");
        eq(inst("<column><text v-for=\"(x,i) in items\" :text=\"i\"/></column>",
                state("items", List.of("a","b"))).children().get(1).str("text", null),
           "1", "v-for 的下标变量");

        // v-for + v-if：先 for 后 if
        eq(inst("<column><text v-for=\"x in nums\" v-if=\"x > 1\" :text=\"x\"/></column>",
                state("nums", List.of(1,2,3))).children().size(), 2, "v-for 再 v-if");

        // ---- 表达式语义 ----
        eq(ev("1 + 2 * 3"),            7,      "优先级");       // 注意：不是 (1+2)*3（骨架原写 9，是笔误）
        eq(ev("(1 + 2) * 3"),          9,      "括号");
        eq(ev("7 / 2"),                3,      "整数除法向零取整");
        eq(ev("-7 / 2"),              -3,      "负数整除向零取整");
        eq(ev("7 % 3"),                1,      "取模");
        eq(ev("1 / 0"),                0,      "除零返回 0 不抛异常");
        eq(ev("'a' + 1"),              "a1",   "字符串拼接数字");
        eq(ev("1 == '1'"),             false,  "不做隐式类型转换");
        eq(ev("0 && 1"),               false,  "&& 返回 bool 不返回操作数");
        eq(ev("[] ? 1 : 2"),           1,      "空数组为真");
        eq(ev("'' ? 1 : 2"),           2,      "空串为假");
        eq(ev("x.y.z", state("x", Map.of())), null, "缺失键返回 null 不报错");
        eq(ev("arr[9]", state("arr", List.of(1))), null, "数组越界返回 null");
        eq(ev("arr.length", state("arr", List.of(1,2))), 2, "length");
        eq(text(null),                 "",     "null 文本化成空串");

        rejects_expr("count++",        "E_EXPR_NO_ASSIGNMENT");
        rejects_expr("f(1)",           "E_EXPR_NO_CALLS");
        rejects_expr("conut",          "E_EXPR_UNKNOWN_IDENT");
        check(errorOf_expr("conut").message().contains("count"), "拼错标识符给出正解");
        rejects_expr("'a' - 1",        "E_EXPR_TYPE");
        rejects_expr(deepExpr(70),     "E_EXPR_TOO_COMPLEX");

        // ---- 事件语句 ----
        eq(run("count++", state("count", 1)).getInt("count"), 2, "自增");
        eq(run("count = count + 5", state("count", 1)).getInt("count"), 6, "赋值");
        var s2 = run("tab = 1; nav('detail')", state("tab", 0));
        eq(s2.getInt("tab"), 1, "多语句按顺序");
        eq(s2.pendingNav(), "detail", "nav 内建");
        // 任一条失败则整组不执行
        var s3 = run("tab = 1; tab = 'x'", state("tab", 0));
        eq(s3.getInt("tab"), 0, "类型不匹配时整组不执行");

        // ---- 静态属性误当变量 ----
        rejects("<progress value=\"count\"/>", "E_TPL_LIKELY_MISSING_COLON");

        // ---- P0 的 script 子集 ----
        rejects("<script>\nfunction f(){}\n</script>" + MIN_TPL, "E_SCRIPT_P0_SUBSET");
        accepts("<script>\nstate = { a: 1 }\n</script>" + MIN_TPL);

        // ============ 以下是骨架之外补的 ============
        splitterForms();
        lineMapping();
        templateRules();
        instantiation();
        expressionTable();
        statementRules();
        scriptRules();
        manifestInline();
        propRulesMatchNodeParser();
        fullExample();
        showcase();
        fuzz();
        errorTable();
        report();
    }

    // ============================================================
    //  骨架用到的助手
    // ============================================================

    static final String MANIFEST = "{\"format\":1,\"id\":\"test:app\",\"version\":\"1.0.0\",\"name\":\"n\",\"author\":\"y\"}";
    /** 开头的换行不能省：骨架里有 "</script>" + MIN_TPL，闭标签必须独占一行。 */
    static final String MIN_TPL = "\n<manifest>\n" + MANIFEST + "\n</manifest>\n<template>\n<box/>\n</template>\n";
    static final String FRAGMENT_SCRIPT = "state = { count: 0, tab: 0, on: true, title: 'x', items: ['a', 'b'] }";

    record Err(String code, int line, int col, String message) {
    }

    record Ran(UiState state, Statements.Outcome outcome) {
        int getInt(String key) {
            return state.getInt(key);
        }

        String pendingNav() {
            return outcome.nav();
        }
    }

    static Map<String, Block> split(String src) {
        return SfcSplitter.split(src);
    }

    /** 没有换行、也不以块标签开头的，是一段模板：包进一份带常用 state 的完整文件。 */
    static String full(String src) {
        boolean fragment = src.indexOf('\n') < 0 && !src.matches("<(manifest|template|script|style)\\b.*");
        if (!fragment) return src;
        return "<manifest>\n" + MANIFEST + "\n</manifest>\n<script>\n" + FRAGMENT_SCRIPT + "\n</script>\n<template>\n"
                + src + "\n</template>\n";
    }

    static Err caught(Runnable body) {
        try {
            body.run();
            return null;
        } catch (SfcError e) {
            return new Err(e.code().name(), e.line(), e.col(), e.getMessage());
        } catch (MssError e) {
            return new Err(e.code().name(), e.line(), e.col(), e.getMessage());
        } catch (RuntimeException | StackOverflowError e) {
            return new Err("!" + e.getClass().getSimpleName(), -1, -1, String.valueOf(e.getMessage()));
        }
    }

    static void rejects(String src, String code) {
        rejected(caught(() -> SfcCompiler.compile("test.vue", full(src))), code, src);
    }

    static void rejects_expr(String src, String code) {
        rejected(caught(() -> ExprParser.expression(src, EXPR_SCOPE, 1, 1)), code, src);
    }

    static void rejected(Err e, String code, String src) {
        rejections++;
        checks++;
        String shown = src.length() > 60 ? src.substring(0, 60) + "…" : src;
        if (e == null) {
            failures.add("应当以 " + code + " 拒绝：" + shown.replace("\n", "⏎"));
        } else if (!e.code.equals(code)) {
            failures.add("应当以 " + code + " 拒绝：" + shown.replace("\n", "⏎") + "  实际 " + e.message);
        } else {
            triggered.add(code);
        }
    }

    static void accepts(String src) {
        Err e = caught(() -> SfcCompiler.compile("test.vue", full(src)));
        check(e == null, "应当通过：" + src.replace("\n", "⏎") + (e == null ? "" : "  实际 " + e.message));
    }

    static Err errorOf(String src) {
        Err e = caught(() -> SfcCompiler.compile("test.vue", full(src)));
        if (e == null) {
            failures.add("本该报错：" + src.replace("\n", "⏎"));
            return new Err("", -1, -1, "");
        }
        triggered.add(e.code);
        return e;
    }

    static final ExprParser.Scope EXPR_SCOPE = ExprParser.Scope.of(Map.of("count", 0, "title", "x", "items", List.of(1, 2)));

    static Err errorOf_expr(String src) {
        Err e = caught(() -> ExprParser.expression(src, EXPR_SCOPE, 1, 1));
        if (e == null) {
            failures.add("本该报错：" + src);
            return new Err("", -1, -1, "");
        }
        return e;
    }

    /** 模板里那一行正好落在原文件第 line 行。 */
    static String withTemplateAtLine(int line, String fragment) {
        StringBuilder sb = new StringBuilder("<manifest>\n" + MANIFEST + "\n</manifest>\n<script>\nstate = { count: 0 }\n</script>\n");
        for (int i = 7; i < line - 1; i++) sb.append('\n');
        return sb + "<template>\n" + fragment + "\n</template>\n";
    }

    static Node compile(String template) {
        return inst(template, UiState.empty());
    }

    static TemplateInstance instance(String template, UiState s) {
        return new TemplateInstance(TemplateCompiler.compile(template, s.values()), "test.vue");
    }

    static Node inst(String template, UiState s) {
        return instance(template, s).instantiate(s);
    }

    static UiState state(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return UiState.of(m);
    }

    static Object ev(String src) {
        return ev(src, UiState.empty());
    }

    static Object ev(String src, UiState s) {
        return ev(src, s, new EvalContext(s.values(), "test.vue"));
    }

    static Object ev(String src, UiState s, EvalContext c) {
        try {
            ExprParser.Typed t = ExprParser.expression(src, ExprParser.Scope.of(s.values()), 1, 1);
            return new Expr.Compiled(t.expr(), 1, src).run(c);
        } catch (SfcError e) {
            failures.add("表达式本该编得过：" + src + "  实际 " + e.getMessage());
            return "!" + e.code();
        }
    }

    static String text(Object v) {
        return Values.text(v);
    }

    static String deepExpr(int ones) {
        return "1" + "+1".repeat(ones - 1);
    }

    static Ran run(String src, UiState s) {
        Statements st = Statements.parse(src, ExprParser.Scope.of(s.values()), 1, 1);
        return new Ran(s, st.run(s, "test.vue", List.of(), List.of()));
    }

    // ============================================================
    //  §9.3 块切分的合法 / 非法形态
    // ============================================================

    static void splitterForms() {
        var crlf = split("<manifest>\r\n{}\r\n</manifest>\r\n<template>\r\n<box/>\r\n</template>\r\n");
        eq(crlf.get("template").content(), "<box/>\n", "CRLF：行尾的 \\r 不进内容，标签照认");
        var bom = split((char) 0xFEFF + "<manifest>\n{}\n</manifest>\n<template>\n<box/>\n</template>");
        eq(bom.get("manifest").startLine(), 2, "开头的 BOM 不算字符");
        var inner = split("<manifest>\n{}\n</manifest>\n<template>\n<column>\n</column>\n</template>\n");
        eq(inner.get("template").content(), "<column>\n</column>\n", "块里顶格的 <column> 是内容，不是块标签");
        var blank = split("\n\n<manifest>\n{}\n</manifest>\n   \n<template>\n<box/>\n</template>\n\n");
        eq(blank.get("template").startLine(), 8, "块外的空行与纯空白行不算游离文本");
        var script = split("<manifest>\n{}\n</manifest>\n<script>\n  </script>\n</script>\n<template>\n<box/>\n</template>");
        eq(script.get("script").content(), "  </script>\n", "缩进的 </script> 是内容");

        rejects("<template> <column/>\n</template>", "E_SFC_BLOCK_FORMAT");
        rejects("<template>\n</template>\n<style >\n</style>", "E_SFC_BLOCK_FORMAT");
        rejects("</template>\n", "E_SFC_UNEXPECTED_CLOSE");
        rejects("<template>\n</style>\n", "E_SFC_MISMATCH");
        rejects("<template>\n<box/>\n</template>\n", "E_SFC_NO_MANIFEST");
        rejects("<manifest>\n" + MANIFEST + "\n</manifest>\n", "E_SFC_NO_TEMPLATE");
        rejects("<!-- 注释 -->\n" + MIN_TPL, "E_SFC_STRAY_TEXT");
        eq(errorOf("<manifest>\n{}\n</manifest>\n<template>\n<box/>\n").line(), 4, "没闭合的块报它的开标签那一行");
        eq(errorOf("\n\n<template>\n\n\n<script>\n</script>").line(), 6, "嵌套报第二个开标签那一行");
    }

    // ============================================================
    //  §9.8 行号映射真验收：每块都有错，逐个修
    // ============================================================

    static void lineMapping() {
        String src = """
<manifest>
{
  "format": 1,
  "id": "demo:lines",
  "name" "行号",
  "version": "1.0",
  "author": "y"
}
</manifest>

<script>
  // 初值
  state = {
    count: 0,
    Title: "x"
  }
</script>

<style>
  .a { gap: 2; }
  .b { colour: $title; }
</style>

<template>
  <column>
    <text>{{ conut }}</text>
    <progress value="count" />
    <divider></divider>
  </column>
</template>
""";
        String[][] steps = {
                // 出错那一行里独有的片段, 修好后的写法, 期望的码
                {"\"name\" \"行号\"", "\"name\": \"行号\"", "E_SFC_MANIFEST"},
                {"\"version\": \"1.0\"", "\"version\": \"1.0.0\"", "E_SFC_MANIFEST"},
                {"colour: $title", "color: $title", "E_UNKNOWN_PROPERTY"},
                {"Title: \"x\"", "title: \"x\"", "E_SCRIPT_STATE"},
                {"{{ conut }}", "{{ count }}", "E_EXPR_UNKNOWN_IDENT"},
                {"value=\"count\"", ":value=\"count\"", "E_TPL_LIKELY_MISSING_COLON"},
                {"<divider></divider>", "<divider />", "E_TPL_MUST_SELF_CLOSE"},
        };
        System.out.println("§9.8 行号映射（错误 → 报的行 → 那一行的内容）：");
        for (String[] step : steps) {
            final String now = src;
            Err e = caught(() -> SfcCompiler.compile("lines.vue", now));
            int expected = lineOf(src, step[0]);
            String[] lines = src.split("\n", -1);
            if (e == null) {
                failures.add("行号映射：本该在第 " + expected + " 行报 " + step[2]);
            } else {
                triggered.add(e.code);
                String shown = e.line >= 1 && e.line <= lines.length ? lines[e.line - 1].strip() : "（越界）";
                System.out.printf("  %-28s → 第 %2d 行 → %s%n", e.code, e.line, shown);
                eq(e.code, step[2], "行号映射：第 " + expected + " 行的错误码");
                eq(e.line, expected, "行号映射：" + step[2] + " 报的行能直接跳到出错那一行");
                check(e.message.contains("lines.vue:" + expected) || e.code.startsWith("E_UNKNOWN") || e.code.startsWith("E_MSS"),
                        "行号映射：文案里写的是 文件名:原始行号 —— " + e.message);
            }
            src = src.replace(step[0], step[1]);
        }
        final String fixed = src;
        Err last = caught(() -> SfcCompiler.compile("lines.vue", fixed));
        check(last == null, "行号映射：逐个修完之后编得过" + (last == null ? "" : "，实际 " + last.message));
    }

    static int lineOf(String src, String marker) {
        String[] lines = src.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(marker)) return i + 1;
        }
        return -1;
    }

    // ============================================================
    //  §9.4 模板语法
    // ============================================================

    static void templateRules() {
        rejects("<colum/>", "E_TPL_UNKNOWN_ELEMENT");
        check(errorOf("<colum/>").message().contains("是不是 'column'"), "未知元素给出最近的猜测");
        check(errorOf("<colum/>").message().contains("tab-bar"), "未知元素列出全部 18 个");
        rejects("<text txt=\"a\"/>", "E_TPL_UNKNOWN_ATTR");
        check(errorOf("<progress vaule=\"1\"/>").message().contains("是不是 'value'"), "未知属性给出最近的猜测");
        rejects("<tab-bar tabs=\"x\" bind=\"tab\"/>", "E_TPL_UNKNOWN_ATTR");
        rejects("<toggle :bind=\"on\"/>", "E_TPL_UNKNOWN_ATTR");
        rejects("<column @click=\"count++\"/>", "E_TPL_EVENT_NOT_ALLOWED");
        rejects("<column><box v-else/></column>", "E_TPL_DANGLING_ELSE");
        rejects("<column><box v-if=\"on\"/><text>x</text><box v-else/></column>", "E_TPL_DANGLING_ELSE");
        rejects("<column><box v-if=\"on\"/><box/><box v-else/></column>", "E_TPL_DANGLING_ELSE");
        rejects("<column><box v-for=\"x in items\" v-if=\"on\"/><box v-else/></column>", "E_TPL_DANGLING_ELSE");
        rejects("<column><box v-if=\"on\"/><box v-else/><box v-else/></column>", "E_TPL_DANGLING_ELSE");
        accepts(full("<column><box v-if=\"on\"/>  <!-- 注释 -->\n  <box v-else/></column>"
                .replace("\n", " ")));
        rejects("<column><text v-for=\"count in items\" :text=\"count\"/></column>", "E_TPL_SHADOW");
        rejects("<column><column v-for=\"a in 2\"><column v-for=\"b in 2\"><box v-for=\"c in 2\"/></column></column></column>",
                "E_TPL_NESTED_FOR");
        rejects("<column><box></column>", "E_TPL_UNCLOSED_TAG");
        rejects("<column><box/>", "E_TPL_UNCLOSED_TAG");
        rejects("<column></row></column>", "E_TPL_SYNTAX");
        rejects("<box/><box/>", "E_TPL_SYNTAX");
        rejects("<box v-if=\"on\"/>", "E_TPL_SYNTAX");
        rejects("<grid cols=\"9\"/>", "E_TPL_BAD_VALUE");
        rejects("<icon/>", "E_TPL_BAD_VALUE");
        rejects("<icon name=\"smile\"/>", "E_TPL_BAD_VALUE");
        rejects("<toggle bind=\"count\"/>", "E_TPL_BAD_VALUE");
        rejects("<toggle bind=\"nope\"/>", "E_EXPR_UNKNOWN_IDENT");
        rejects("<text text=\"a\">b</text>", "E_TPL_SYNTAX");
        rejects("<divider></divider>", "E_TPL_MUST_SELF_CLOSE");
        rejects("<text><box/></text>", "E_TPL_MUST_SELF_CLOSE");
        rejects("<box class=\"Big\"/>", "E_TPL_BAD_VALUE");
        rejects("<box v-else=\"x\"/>", "E_TPL_SYNTAX");
        rejects("<column><box v-for=\"x of items\"/></column>", "E_TPL_SYNTAX");
        rejects("<progress value=\"1\" :value=\"2\"/>", "E_TPL_SYNTAX");
        rejects("<text>{{ count </text>", "E_TPL_SYNTAX");
        rejects("<column><text v-for=\"x in title\" :text=\"x\"/></column>", "E_TPL_SYNTAX");
        rejects("<button @click=\"count = count +\">x</button>", "E_EXPR_SYNTAX");
        check(errorOf("<column>\n<text>{{ 'a' - 1 }}</text></column>".replace("\n", " ")).col() > 0, "表达式错误带列号");
    }

    // ============================================================
    //  §9.9 实例化
    // ============================================================

    static void instantiation() {
        Node row = inst("<row><text v-for=\"n in 3\" :text=\"n\"/></row>", UiState.empty());
        eq(row.children().stream().map(c -> c.str("text", null)).toList(), List.of("1", "2", "3"), "x in 3 取 1..3");
        eq(inst("<row><text v-for=\"n in count\" :text=\"n\"/></row>", state("count", -2)).children().size(), 0,
                "x in 负数 是空的");

        UiState big = state("count", 0);
        TemplateInstance many = instance("<list item-height=\"8\"><text v-for=\"n in 3000\" :text=\"n\"/></list>", big);
        Node list = many.instantiate(big);
        eq(list.children().size(), NodeParser.MAX_NODES - 1, "节点总数 512 在实例化时截断");
        check(many.truncated(), "截断记在 truncated() 上");
        check(many.warnings().stream().anyMatch(w -> w.contains("2048")), "v-for 超过 2048 项截断并 warn");
        check(many.warnings().stream().anyMatch(w -> w.contains("512")), "节点超限 warn");

        UiState counter = state("count", 50);
        TemplateInstance progress = instance("<progress :value=\"count * 10\"/>", counter);
        eq(progress.instantiate(counter).num("value", -1), 100, "绑定值超出范围时夹紧");
        check(!progress.warnings().isEmpty(), "夹紧记 warn");

        eq(inst("<text>{{ a }}{{ b }}</text>", state("a", 1, "b", 2)).str("text", null), "12", "两个 int 插值是拼接不是相加");
        eq(compile("<text>  a\n   &lt;b&gt;  &amp; </text>").str("text", null), "a <b> &", "空白压缩、实体解码");
        Node mixed = compile("<column>前 <box/> 后</column>");
        eq(mixed.children().size(), 3, "容器里的文字与元素各成节点");
        eq(mixed.children().get(0).str("text", null), "前", "文字节点按出现顺序");
        eq(compile("<button>+1</button>").str("text", null), "+1", "只有文字的按钮编成 text 属性");
        eq(compile("<button><icon name=\"plus\"/>加</button>").children().size(), 2, "有子元素的按钮文字成子节点");

        Node chain = inst("<column><text v-if=\"tab == 0\">a</text><text v-else-if=\"tab == 1\">b</text><text v-else>c</text></column>",
                state("tab", 1));
        eq(chain.children().get(0).str("text", null), "b", "v-else-if 分支");

        UiState picks = state("picked", 0);
        TemplateInstance buttons = instance("<column><button v-for=\"n in 3\" @click=\"picked = n\">x</button></column>", picks);
        Node col = buttons.instantiate(picks);
        buttons.clickOf(col.children().get(2)).run(picks, "test.vue");
        eq(picks.getInt("picked"), 3, "@click 捕获实例化时的循环变量");
        eq(buttons.clickOf(col), null, "没写 @click 的节点查不到动作");

        UiState keyed = state("rows", List.of(Map.of("id", "a"), Map.of("id", "b"), Map.of("id", "a")));
        TemplateInstance keys = instance("<column><box v-for=\"r in rows\" :key=\"r.id\"/></column>", keyed);
        Node keyedTree = keys.instantiate(keyed);
        eq(keyedTree.children().get(1).props().get("key"), "b", ":key 进 props.key");
        eq(keyedTree.children().get(2).props().get("key"), null, "重复的 :key 不写");
        check(keys.warnings().stream().anyMatch(w -> w.contains("重复")), "重复的 :key 记 warn");

        UiState ids = state("count", 0);
        TemplateInstance dupIds = instance("<column><box v-for=\"n in 2\" id=\"cell\"/></column>", ids);
        Node idTree = dupIds.instantiate(ids);
        eq(idTree.children().get(1).id(), null, "v-for 展开出来的重复 id 只留第一个");

        UiState tabs = state("tab", 0, "names", List.of("甲"));
        TemplateInstance badTabs = instance("<tab-bar bind=\"tab\" :tabs=\"names\"/>", tabs);
        eq(badTabs.instantiate(tabs).props().get("tabs"), null, "不合规的 :tabs 丢掉而不是抛");
    }

    // ============================================================
    //  §9.5 表达式语义表
    // ============================================================

    static void expressionTable() {
        eq(ev("2147483647 + 1"), Integer.MIN_VALUE, "整数溢出按 Java 回绕");
        eq(ev("-2147483648"), Integer.MIN_VALUE, "int 最小值写得出来");
        eq(ev("-7 % 3"), -1, "取模符号跟被除数");
        eq(ev("'b' > 'a'"), true, "字符串按码元比较");
        eq(ev("'a' + null"), "a", "null 拼进字符串是空串");
        eq(ev("null == null"), true, "null 等于 null");
        eq(ev("[1, 2] == [1, 2]"), true, "数组按内容比较");
        eq(ev("true == 1"), false, "bool 与 int 不相等");
        eq(ev("!''"), true, "! 按真假判定取反");
        eq(ev("{} ? 1 : 2"), 1, "空对象为真");
        eq(ev("1 || 0"), true, "|| 返回 bool");
        eq(ev("'x' && 'y'"), true, "&& 两边都真时是 true 不是 'y'");
        eq(ev("true ? 1 : false ? 2 : 3"), 1, "?: 右结合");
        eq(ev("'abc'.length"), 3, "字符串的 length");
        eq(ev("o.length", state("o", Map.of("length", 5))), 5, "对象上的 length 是普通键");
        eq(ev("n.length", state("n", 3)), null, "int 上的 length 是 null");
        eq(ev("o['a']", state("o", Map.of("a", 1))), null, "对象不能用 [] 取");
        eq(ev("(1 + 2) * (3 + 4) - 10 / 3 % 2"), 20, "混合优先级");

        UiState longText = state("s", "x".repeat(64));
        EvalContext concat = new EvalContext(longText.values(), "test.vue");
        Object joined = ev(String.join("+", java.util.Collections.nCopies(17, "s")), longText, concat);
        eq(joined instanceof String j ? j.length() : -1, EvalContext.MAX_CONCAT, "拼接结果截到 1024");
        check(!concat.warnings().isEmpty(), "截断记 warn");

        EvalContext deep = new EvalContext(UiState.empty().values(), "test.vue");
        eq(ev("!".repeat(40) + "1", UiState.empty(), deep), null, "求值超过 32 层得 null");
        check(!deep.warnings().isEmpty(), "超深记 warn");

        EvalContext budget = new EvalContext(UiState.empty().values(), "test.vue");
        Expr.Compiled one = new Expr.Compiled(ExprParser.expression("1", ExprParser.Scope.of(Map.of()), 1, 1).expr(), 1, "1");
        Object lastValue = null;
        for (int i = 0; i < EvalContext.MAX_EVALS + 10; i++) lastValue = one.run(budget);
        eq(lastValue, null, "一次重排超过 4096 次求值后按 null");
        check(budget.exhausted(), "求值预算用完有标记");

        EvalContext typeErr = new EvalContext(state("t", "a", "n", 1).values(), "test.vue");
        eq(ev("t.x - n", state("t", Map.of("x", "a"), "n", 1), typeErr), null, "运行期类型不对得 null");
        check(!typeErr.warnings().isEmpty(), "运行期类型不对记 warn");

        rejects_expr("count = 1", "E_EXPR_NO_ASSIGNMENT");
        rejects_expr("count += 1", "E_EXPR_NO_ASSIGNMENT");
        rejects_expr("count === 1", "E_EXPR_SYNTAX");
        check(errorOf_expr("count === 1").message().contains("=="), "=== 给出改法");
        rejects_expr("count ?? 1", "E_EXPR_SYNTAX");
        rejects_expr("1.5", "E_EXPR_SYNTAX");
        rejects_expr("'abc", "E_EXPR_SYNTAX");
        rejects_expr("2147483648", "E_EXPR_SYNTAX");
        rejects_expr("count & 1", "E_EXPR_SYNTAX");
        rejects_expr("x => 1", "E_EXPR_UNKNOWN_IDENT");
        rejects_expr("'" + "a".repeat(260) + "'", "E_EXPR_TOO_LONG");
        rejects_expr("-title", "E_EXPR_TYPE");
        rejects_expr("title < 1", "E_EXPR_TYPE");
        rejects_expr("true * 2", "E_EXPR_TYPE");
        rejects_expr("null + 1", "E_EXPR_TYPE");
        rejects_expr("items.map(1)", "E_EXPR_NO_CALLS");
        rejects_expr("[" + "1,".repeat(32) + "1]", "E_EXPR_SYNTAX");
        rejects_expr("", "E_EXPR_SYNTAX");
    }

    // ============================================================
    //  §9.6 事件语句
    // ============================================================

    static void statementRules() {
        eq(run("count--", state("count", 1)).getInt("count"), 0, "自减");
        Ran closing = run("close()", state("count", 1));
        check(closing.outcome().applied() && closing.outcome().close(), "close() 内建");
        check(run("back()", state("count", 1)).outcome().back(), "back() 内建");
        Ran bad = run("count = 5; count++; nav(1)", state("count", 1));
        eq(bad.getInt("count"), 1, "nav 的参数不是字符串时前面的赋值也不生效");
        check(!bad.outcome().applied() && !bad.outcome().warnings().isEmpty(), "整组不执行时记 warn");
        UiState rev = state("title", "a", "count", 0);
        run("count = 3; title++", rev);
        eq(rev.revision(), 0, "失败的一组不动 revision");
        eq(run("count = count + 1;", state("count", 1)).getInt("count"), 2, "末尾的 ; 可以写");

        rejectsStatement("count += 1", "E_EXPR_SYNTAX");
        rejectsStatement("a; b; c; d; e", "E_EXPR_SYNTAX");
        rejectsStatement("nope = 1", "E_EXPR_UNKNOWN_IDENT");
        rejectsStatement("alert(1)", "E_EXPR_NO_CALLS");
        rejectsStatement("count", "E_EXPR_SYNTAX");
        rejectsStatement("count = 1 count = 2", "E_EXPR_SYNTAX");
        rejects("<column><button v-for=\"n in 3\" @click=\"n = 1\">x</button></column>", "E_EXPR_SYNTAX");
        rejects("<button @click=\"\">x</button>", "E_TPL_SYNTAX");
    }

    static void rejectsStatement(String src, String code) {
        rejected(caught(() -> Statements.parse(src, EXPR_SCOPE, 1, 1)), code, src);
    }

    // ============================================================
    //  §9.7 script 子集
    // ============================================================

    static void scriptRules() {
        eq(ScriptParser.parse("\n  \n"), Map.of(), "空的 script 是空 state");
        Map<String, Object> s = ScriptParser.parse("""
  // 注释
  /* 块注释 */
  state = {
    "quoted": 1,
    neg: -3,
    tabIndex: 0,
    rows: [{ id: 'a', n: 1 }, { id: 'b', n: 2 },],
  };
""");
        eq(s.get("quoted"), 1, "键可以带引号");
        eq(s.get("neg"), -3, "负数初值");
        eq(s.get("rows"), List.of(Map.of("id", "a", "n", 1), Map.of("id", "b", "n", 2)), "数组套对象、尾逗号");
        eq(s.keySet().stream().toList(), List.of("quoted", "neg", "tabIndex", "rows"), "state 保持书写顺序");

        rejects("<script>\nstate = 1\n</script>" + MIN_TPL, "E_SCRIPT_P0_SUBSET");
        rejects("<script>\nlet state = {}\n</script>" + MIN_TPL, "E_SCRIPT_P0_SUBSET");
        rejects("<script>\nstate = { a: b }\n</script>" + MIN_TPL, "E_SCRIPT_P0_SUBSET");
        rejects("<script>\nstate = { a: 1 }\nstate = { b: 2 }\n</script>" + MIN_TPL, "E_SCRIPT_P0_SUBSET");
        rejects("<script>\nstate = { a: 1 + 1 }\n</script>" + MIN_TPL, "E_SCRIPT_P0_SUBSET");
        rejects("<script>\nstate = { a: [[[1]]] }\n</script>" + MIN_TPL, "E_SCRIPT_STATE");
        rejects("<script>\nstate = { a: [1, 'x'] }\n</script>" + MIN_TPL, "E_SCRIPT_STATE");
        rejects("<script>\nstate = { a: null }\n</script>" + MIN_TPL, "E_SCRIPT_STATE");
        rejects("<script>\nstate = { a: '" + "x".repeat(65) + "' }\n</script>" + MIN_TPL, "E_SCRIPT_STATE");
        rejects("<script>\nstate = { _a: 1 }\n</script>" + MIN_TPL, "E_SCRIPT_STATE");
        rejects("<script>\nstate = { a: 1, a: 2 }\n</script>" + MIN_TPL, "E_SCRIPT_STATE");
        StringBuilder many = new StringBuilder("<script>\nstate = {");
        for (int i = 0; i <= 16; i++) many.append(" k").append(i).append(": 0,");
        rejects(many + " }\n</script>" + MIN_TPL, "E_SCRIPT_STATE");
        rejects("<manifest>\n" + MANIFEST + "\n</manifest>\n<template>\n<text>{{ count }}</text>\n</template>\n",
                "E_EXPR_UNKNOWN_IDENT");
    }

    // ============================================================
    //  内联 manifest
    // ============================================================

    static void manifestInline() {
        Manifest m = Manifest.parseInline(MANIFEST);
        eq(m.id(), "test:app", "内联 manifest 的 id");
        eq(m.uiTree(), null, "内联 manifest 没有 ui.tree");
        eq(m.icon(), null, "icon 可省");
        String withIcon = MANIFEST.replace("}", ",\"icon\":\"data:image/png;base64,iVBORw0KGgo=\"}");
        eq(Manifest.parseInline(withIcon).icon(), "data:image/png;base64,iVBORw0KGgo=", "icon 是 data URI");
        eq(pkgCode(() -> Manifest.parseInline(MANIFEST.replace("}", ",\"icon\":\"icon.png\"}"))),
                PackageError.Code.E_PKG_BAD_ICON, "内联 icon 不收包内路径");
        eq(pkgCode(() -> Manifest.parseInline(MANIFEST.replace("}", ",\"icon\":\"data:image/png;base64,"
                        + "A".repeat(Manifest.MAX_INLINE_ICON + 4) + "\"}"))),
                PackageError.Code.E_PKG_BAD_ICON, "内联 icon 上限 8 KiB");
        eq(pkgCode(() -> Manifest.parseInline(MANIFEST.replace("\"name\":\"n\",", ""))),
                PackageError.Code.E_PKG_MISSING_FIELD, "内联 manifest 的 name 仍然必填");
        eq(pkgCode(() -> Manifest.parse(MANIFEST)), PackageError.Code.E_PKG_MISSING_FIELD, "zip 里的 manifest.json 规则没变");
        eq(errorOf("<manifest>\n{\n\"format\": 1,\n\"id\": \"test:app\",\n\"id\": \"x:y\"\n}\n</manifest>\n<template>\n<box/>\n</template>").line(),
                5, "重复键报第二次出现的那一行");
    }

    static PackageError.Code pkgCode(Runnable r) {
        try {
            r.run();
            return null;
        } catch (PackageError e) {
            return e.code();
        }
    }

    // ============================================================
    //  模板的取值范围与 IR 的判据一致
    // ============================================================

    static void propRulesMatchNodeParser() {
        String[][] cases = {
                {"grid", "cols", ""}, {"list", "item-height", ""}, {"spacer", "size", ""},
                {"image", "w", "\"src\":\"a.png\","}, {"image", "h", "\"src\":\"a.png\","},
                {"icon", "size", "\"name\":\"info\","}, {"item", "count", "\"item\":\"minecraft:stone\","},
                {"item", "size", "\"item\":\"minecraft:stone\","}, {"badge", "count", ""},
                {"progress", "value", ""}, {"progress", "height", ""},
        };
        int compared = 0;
        for (String[] c : cases) {
            PropRules.Spec spec = PropRules.of(NodeType.of(c[0])).get(c[1]);
            List<Long> values = new ArrayList<>(List.of((long) spec.min() - 1, (long) spec.min(), (long) spec.max()));
            if (spec.max() != Integer.MAX_VALUE) values.add((long) spec.max() + 1);
            for (long v : values) {
                String node = "{\"type\":\"" + c[0] + "\"," + c[2] + "\"" + c[1] + "\":" + v + "}";
                boolean ir = acceptsIr("{\"pages\":{\"main\":{\"type\":\"column\",\"children\":[" + node + "]}},\"entry\":\"main\"}");
                boolean tpl;
                try {
                    PropRules.parseStatic(spec, String.valueOf(v));
                    tpl = true;
                } catch (PropRules.Bad bad) {
                    tpl = false;
                }
                eq(tpl, ir, c[0] + "." + c[1] + "=" + v + " 模板与 IR 判得一样");
                compared++;
            }
        }
        check(compared > 30, "取值边界对打了 " + compared + " 组");
        eq(ScriptNodeTest.onePage("{\"type\":\"box\"}").isEmpty(), false, "借用 S2 的造树助手");
    }

    static boolean acceptsIr(String json) {
        try {
            NodeParser.parse(json);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ============================================================
    //  §9.2 完整示例 ⇔ §4.2.1 的等价 JSON
    // ============================================================

    /** §9.2 原文；icon 的 "iVBORw0KGgo..." 是文档里的省略号，换成一段合法的 base64。 */
    static final String COUNTER = """
<manifest>
{
  "format": 1,
  "id": "example:counter",
  "version": "1.0.0",
  "name": "计数器",
  "author": "yumeka",
  "icon": "data:image/png;base64,iVBORw0KGgo="
}
</manifest>

<template>
  <column id="root">
    <text class="title">{{ title }}</text>
    <divider />

    <tab-bar bind="tab" :tabs="[{text:'计数'},{text:'关于'}]" />

    <column v-if="tab == 0" class="body">
      <text class="big">{{ count }}</text>
      <row class="actions">
        <button class="btn" @click="count = count + 1">+1</button>
        <button class="btn" @click="count = 0" :enabled="count > 0">清零</button>
      </row>
      <progress :value="count * 10" />
    </column>

    <scroll v-else class="body">
      <text v-for="line in about" :text="line" />
    </scroll>
  </column>
</template>

<script>
  state = {
    title: "计数器",
    count: 0,
    tab: 0,
    about: ["一个例子", "作者 yumeka"]
  }
</script>

<style>
  #root    { padding: 6; gap: 4; background: $screen; }
  .title   { color: $title; }
  .body    { gap: 4; grow: 1; }
  .big     { color: $accent; text-align: center; }
  .actions { layout: row; gap: 4; }
  .btn     { grow: 1; text-align: center; }
</style>
""";

    static final String COUNTER_STATE = "{\"title\":\"计数器\",\"count\":0,\"tab\":0,\"about\":[\"一个例子\",\"作者 yumeka\"]}";

    static final String COUNTER_TAB0 = "{\"state\":" + COUNTER_STATE + ",\"pages\":{\"main\":"
            + "{\"type\":\"column\",\"id\":\"root\",\"children\":["
            + "{\"type\":\"text\",\"class\":[\"title\"],\"text\":\"计数器\"},"
            + "{\"type\":\"divider\"},"
            + "{\"type\":\"tab-bar\",\"bind\":\"tab\",\"tabs\":[{\"text\":\"计数\"},{\"text\":\"关于\"}]},"
            + "{\"type\":\"column\",\"class\":[\"body\"],\"children\":["
            + "{\"type\":\"text\",\"class\":[\"big\"],\"text\":\"0\"},"
            + "{\"type\":\"row\",\"class\":[\"actions\"],\"children\":["
            + "{\"type\":\"button\",\"class\":[\"btn\"],\"text\":\"+1\",\"onClick\":{\"set\":{\"count\":1}}},"
            + "{\"type\":\"button\",\"class\":[\"btn\"],\"text\":\"清零\",\"enabled\":false,\"onClick\":{\"set\":{\"count\":0}}}]},"
            + "{\"type\":\"progress\",\"value\":0}]}]}},\"entry\":\"main\"}";

    static final String COUNTER_TAB1 = "{\"state\":" + COUNTER_STATE.replace("\"tab\":0", "\"tab\":1") + ",\"pages\":{\"main\":"
            + "{\"type\":\"column\",\"id\":\"root\",\"children\":["
            + "{\"type\":\"text\",\"class\":[\"title\"],\"text\":\"计数器\"},"
            + "{\"type\":\"divider\"},"
            + "{\"type\":\"tab-bar\",\"bind\":\"tab\",\"tabs\":[{\"text\":\"计数\"},{\"text\":\"关于\"}]},"
            + "{\"type\":\"scroll\",\"class\":[\"body\"],\"children\":["
            + "{\"type\":\"text\",\"text\":\"一个例子\"},{\"type\":\"text\",\"text\":\"作者 yumeka\"}]}]}},\"entry\":\"main\"}";

    static void fullExample() {
        SfcCompiler.App app = SfcCompiler.compile("counter.vue", COUNTER);
        eq(app.manifest().id(), "example:counter", "§9.2 的 manifest");
        eq(app.stylesheet().ruleCount(), 6, "§9.2 的 6 条样式");

        for (String[] variant : new String[][]{{"0", COUNTER_TAB0}, {"1", COUNTER_TAB1}}) {
            NodeParser.Ui ir = NodeParser.parse(variant[1]);
            UiState st = UiState.of(app.template().initialState());
            if (variant[0].equals("1")) st.set("tab", 1);
            eq(st.values(), ir.state().entrySet().stream().collect(
                    LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll), "tab=" + variant[0] + " 时 state 逐字段相等");
            TemplateInstance ti = new TemplateInstance(app.template(), "counter.vue");
            Node sfc = ti.instantiate(st);
            check(ti.warnings().isEmpty(), "§9.2 实例化没有 warn：" + ti.warnings());
            System.out.println("§9.2 tab=" + variant[0] + " dump（模板）：" + dump(sfc, ti));
            System.out.println("§9.2 tab=" + variant[0] + " dump（IR）  ：" + dump(ir.root(), null));
            eq(dump(sfc, ti), dump(ir.root(), null), "tab=" + variant[0] + " 两边 dump 逐字相同");
            sameTree(sfc, ir.root(), st, ti, "tab=" + variant[0] + " root");
        }
    }

    /** 逐字段比。onClick 的 §4.5 形态装不下 count = count + 1，所以比的是在同一份 state 上执行之后的结果。 */
    static void sameTree(Node sfc, Node ir, UiState st, TemplateInstance ti, String path) {
        eq(sfc.type(), ir.type(), path + " 的 type");
        eq(sfc.id(), ir.id(), path + " 的 id");
        eq(sfc.classes(), ir.classes(), path + " 的 class");
        eq(sfc.props(), ir.props(), path + " 的 props");
        eq(sfc.showIf(), ir.showIf(), path + " 的 showIf");
        Statements.Bound click = ti.clickOf(sfc);
        if (ir.onClick() != null || click != null) {
            check(ir.onClick() != null && click != null, path + " 两边都有点击动作");
            if (ir.onClick() != null && click != null) {
                UiState viaIr = UiState.of(st.values());
                UiState viaSfc = UiState.of(st.values());
                Node.Action action = ir.onClick();
                action.set().forEach(viaIr::set);
                if (action.toggle() != null) viaIr.toggle(action.toggle());
                click.run(viaSfc, "counter.vue");
                eq(viaSfc.values(), viaIr.values(), path + " 点一下之后 state 相同");
            }
        }
        eq(sfc.children().size(), ir.children().size(), path + " 的子节点数");
        for (int i = 0; i < Math.min(sfc.children().size(), ir.children().size()); i++) {
            sameTree(sfc.children().get(i), ir.children().get(i), st, ti, path + ".children[" + i + "]");
        }
    }

    static String dump(Node n, TemplateInstance ti) {
        StringBuilder sb = new StringBuilder("{").append(n.type().json);
        if (n.id() != null) sb.append(" #").append(n.id());
        for (String c : n.classes()) sb.append(" .").append(c);
        if (!n.props().isEmpty()) sb.append(" ").append(new TreeMap<>(n.props()));
        boolean clickable = ti != null ? ti.clickOf(n) != null : n.onClick() != null;
        if (clickable) sb.append(" @click");
        for (Node c : n.children()) sb.append(" ").append(dump(c, ti));
        return sb.append("}").toString();
    }

    // ============================================================
    //  附录 D 的 showcase.vue
    // ============================================================

    static void showcase() throws Exception {
        Path file = findUp("docs/samples/showcase.vue");
        check(file != null, "找得到 docs/samples/showcase.vue");
        if (file == null) return;
        String src = Files.readString(file, StandardCharsets.UTF_8);
        Err e = caught(() -> SfcCompiler.compile("showcase.vue", src));
        check(e == null, "showcase.vue 编得过" + (e == null ? "" : "：" + e.message));
        if (e != null) return;
        SfcCompiler.App app = SfcCompiler.compile("showcase.vue", src);
        for (int tab = 0; tab < 3; tab++) {
            UiState st = UiState.of(app.template().initialState());
            st.set("tab", tab);
            TemplateInstance ti = new TemplateInstance(app.template(), "showcase.vue");
            Node root = ti.instantiate(st);
            check(ti.warnings().isEmpty() && !ti.truncated(), "showcase tab=" + tab + " 实例化没有 warn：" + ti.warnings());
            check(root.children().size() == 3, "showcase tab=" + tab + " 根下是标题行、分段和一个页面");
        }
    }

    static Path findUp(String relative) {
        for (Path p = Paths.get("").toAbsolutePath(); p != null; p = p.getParent()) {
            if (Files.exists(p.resolve(relative))) return p.resolve(relative);
        }
        return null;
    }

    // ============================================================
    //  fuzz：只许抛 SfcError / MssError
    // ============================================================

    static final String[] SOUP = {
            "<manifest>", "</manifest>", "<template>", "</template>", "<script>", "</script>", "<style>", "</style>",
            "\n", "\n", " ", "  ", "<column>", "</column>", "<text>", "</text>", "<button ", "@click=\"", ":value=\"",
            "v-for=\"x in items\"", "v-if=\"", "v-else", "{{", "}}", "\"", "'", "=", ">", "/>", "<", "</", "count", "+",
            "-", "(", ")", "[", "]", "{", "}", ",", ":", ";", "state = {", "1", "2147483648", "&lt;", "<!--", "-->",
            MANIFEST, ".a { gap: 2; }", "(x, i) in ", "nav('a')", "++", "x.y", "\\", "é",
    };

    static void fuzz() {
        Random random = new Random(20260915L);
        Map<String, Integer> kinds = new TreeMap<>();
        List<String> others = new ArrayList<>();
        UiState st = UiState.of(Map.of("count", 0, "items", List.of("a", "b"), "tab", 0));
        ExprParser.Scope scope = ExprParser.Scope.of(st.values());
        int inputs = 5000;
        for (int i = 0; i < inputs; i++) {
            String input = switch (i % 3) {
                case 0 -> randomBytes(random);
                case 1 -> mutate(COUNTER, random);
                default -> soup(random);
            };
            tally(kinds, others, input, "split", caught(() -> SfcSplitter.split(input)));
            tally(kinds, others, input, "template", caught(() -> {
                TemplateInstance ti = new TemplateInstance(TemplateCompiler.compile(input, st.values()), "fuzz.vue");
                ti.instantiate(st);
            }));
            String expr = input.length() > 300 ? input.substring(0, 300) : input;
            tally(kinds, others, input, "expr", caught(() -> ExprParser.expression(expr, scope, 1, 1)));
            tally(kinds, others, input, "sfc", caught(() -> {
                SfcCompiler.App app = SfcCompiler.compile("fuzz.vue", input);
                UiState s = UiState.of(app.template().initialState());
                new TemplateInstance(app.template(), "fuzz.vue").instantiate(s);
            }));
        }
        System.out.println("fuzz：" + inputs + " 个输入 × 4 个入口，结果分布：");
        kinds.forEach((k, v) -> System.out.printf("  %-28s %d%n", k, v));
        check(others.isEmpty(), "fuzz 只抛 SfcError / MssError，其他异常的样本：" + others);
        check(kinds.getOrDefault("通过", 0) > 0, "fuzz 里有编得过的输入，变异样本没有全部落在第一道闸上");
    }

    static void tally(Map<String, Integer> kinds, List<String> others, String input, String entry, Err e) {
        String key = e == null ? "通过" : e.code;
        kinds.merge(key, 1, Integer::sum);
        if (e != null && e.code.startsWith("!") && others.size() < 3) {
            others.add(entry + " " + e.code + " " + e.message + " ← " + input.replace("\n", "⏎"));
        }
    }

    static String randomBytes(Random r) {
        byte[] bytes = new byte[r.nextInt(300)];
        r.nextBytes(bytes);
        return r.nextBoolean() ? new String(bytes, StandardCharsets.UTF_8) : new String(bytes, StandardCharsets.ISO_8859_1);
    }

    static String mutate(String base, Random r) {
        StringBuilder sb = new StringBuilder(base);
        int edits = 1 + r.nextInt(6);
        String alphabet = "<>/{}\"'=:@-\n .v()[]+;&!?0123456789abcxyz";
        for (int k = 0; k < edits && sb.length() > 0; k++) {
            int at = r.nextInt(sb.length());
            char c = r.nextInt(5) == 0 ? (char) r.nextInt(0x10000) : alphabet.charAt(r.nextInt(alphabet.length()));
            switch (r.nextInt(3)) {
                case 0 -> sb.setCharAt(at, c);
                case 1 -> sb.insert(at, c);
                default -> sb.deleteCharAt(at);
            }
        }
        return sb.toString();
    }

    static String soup(Random r) {
        StringBuilder sb = new StringBuilder();
        int n = r.nextInt(60);
        for (int k = 0; k < n; k++) sb.append(SOUP[r.nextInt(SOUP.length)]);
        return sb.toString();
    }

    // ============================================================
    //  §9.11 全表
    // ============================================================

    static final String[][] SPEC = {
            {"E_SFC_BLOCK_FORMAT", "%s:%d 顶层块标签必须独占一行并从第 1 列开始"},
            {"E_SFC_UNKNOWN_BLOCK", "%s:%d 不认识的块 <%s>。只有 manifest / template / script / style"},
            {"E_SFC_DUP_BLOCK", "%s:%d <%s> 出现了两次，每块只能有一个"},
            {"E_SFC_NESTED_BLOCK", "%s:%d <%s> 还没闭合就开了 <%s>，顶层块不能嵌套"},
            {"E_SFC_UNEXPECTED_CLOSE", "%s:%d 多余的 </%s>"},
            {"E_SFC_MISMATCH", "%s:%d 开的是 <%s>，闭的是 </%s>"},
            {"E_SFC_UNCLOSED_BLOCK", "%s:%d <%s> 没有闭合"},
            {"E_SFC_STRAY_TEXT", "%s:%d 块外面不能有内容"},
            {"E_SFC_NO_MANIFEST", "%s 缺少 <manifest> 块"},
            {"E_SFC_NO_TEMPLATE", "%s 缺少 <template> 块"},
            {"E_TPL_MUST_SELF_CLOSE", "%s:%d <%s> 不能有子元素，写成 <%s />"},
            {"E_TPL_UNKNOWN_ATTR", "%s:%d <%s> 没有属性 '%s'%s。它接受：%s"},
            {"E_TPL_LIKELY_MISSING_COLON", "%s:%d %s=\"%s\" 是字符串。要用变量写成 :%s=\"%s\""},
            {"E_TPL_DANGLING_ELSE", "%s:%d v-else 前面必须紧跟 v-if 或 v-else-if"},
            {"E_TPL_SHADOW", "%s:%d v-for 的变量 '%s' 和 state 里的同名，换个名字"},
            {"E_TPL_NESTED_FOR", "%s:%d v-for 嵌套最多 2 层"},
            {"E_TPL_EVENT_NOT_ALLOWED", "%s:%d <%s> 不接受 @click，只有 button / toggle / tab-bar 可以"},
            {"E_TPL_UNCLOSED_TAG", "%s:%d <%s> 没有闭合"},
            {"E_EXPR_SYNTAX", "%s:%d 表达式语法错误：%s"},
            {"E_EXPR_UNKNOWN_IDENT", "%s:%d 不认识 '%s'%s。已声明的有：%s"},
            {"E_EXPR_NO_ASSIGNMENT", "%s:%d {{ }} 里不能赋值，赋值只能写在 @click 里"},
            {"E_EXPR_NO_CALLS", "%s:%d P0 不支持调用方法，P1 开放 <script>"},
            {"E_EXPR_TYPE", "%s:%d '%s' 两边类型是 %s 和 %s，这个运算不支持"},
            {"E_EXPR_TOO_COMPLEX", "%s:%d 表达式太复杂（%d 个节点，上限 64），拆成 <script> 里的计算"},
            {"E_EXPR_TOO_LONG", "%s:%d 表达式超过 256 字符"},
            {"E_SCRIPT_P0_SUBSET", "%s:%d P0 的 <script> 只能写 state = { ... }，方法与逻辑在 P1 开放"},
    };

    /** 与 §9.11 不逐字相同、或 §9.11 没有的码，各带理由。 */
    static final Map<String, String> DEVIATIONS = Map.of(
            "E_TPL_UNKNOWN_ELEMENT", "§9.11 文案末尾的 ... 换成 %s，填实际的 18 个名字",
            "E_SFC_MANIFEST", "§9.11 没有 <manifest> 的码；包一层 PackageError，前面补 文件:行",
            "E_TPL_SYNTAX", "§9.11 没有模板语法错的码：多余的闭标签、多个根、属性写两次、v-for 写法不对",
            "E_TPL_BAD_VALUE", "§9.11 没有静态属性取值不合规的码；对应 §4.9 的 E_BAD_VALUE",
            "E_SCRIPT_STATE", "§9.7 的键名、个数、长度、同构、层数没有对应的码");

    static void errorTable() {
        Set<String> covered = new TreeSet<>();
        for (String[] row : SPEC) {
            eq(SfcError.Code.valueOf(row[0]).text(), row[1], row[0] + " 的文案与 §9.11 逐字相同");
            covered.add(row[0]);
        }
        covered.addAll(DEVIATIONS.keySet());
        for (SfcError.Code c : SfcError.Code.values()) {
            check(covered.contains(c.name()), c.name() + " 要么与 §9.11 逐字相同，要么登记成有理由的出入");
        }
        eq(covered.size(), SfcError.Code.values().length, "没有登记了却不存在的码");

        boolean threw = false;
        try {
            SfcError.at(SfcError.Code.E_TPL_SHADOW, 1, 1);
        } catch (IllegalStateException e) {
            threw = true;
        }
        check(threw, "模板要的参数没给够时当场抛");

        System.out.println("§9.11 错误码触发情况：");
        Set<String> missing = new TreeSet<>();
        for (SfcError.Code c : SfcError.Code.values()) {
            boolean hit = triggered.contains(c.name());
            System.out.printf("  %-28s %s%n", c.name(), hit ? "有触发样例" : "没有触发样例");
            if (!hit) missing.add(c.name());
        }
        check(missing.isEmpty(), "每个错误码都有触发样例，缺的是：" + missing);
    }
}
