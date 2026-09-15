package com.november.mcphone.core.script.sfc;

import com.november.mcphone.core.script.layout.StateRules;
import com.november.mcphone.core.script.layout.UiState;
import com.november.mcphone.core.script.sfc.ExprParser.Scope;
import com.november.mcphone.core.script.sfc.ExprParser.Tok;
import com.november.mcphone.core.script.sfc.ExprParser.Typed;
import com.november.mcphone.core.script.sfc.SfcError.Code;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@code @click} 的值（施工方案 §9.6）：赋值、自增减、内建 close() / back() / nav(...)，用 ; 连接最多 4 条。
 * 它不是表达式语言的一部分，所以 {@code {{ count++ }}} 报的是不能赋值。
 *
 * <p>按书写顺序在一份副本上执行，全部成功才写回 state：半执行的状态比不执行更难排查。
 */
public final class Statements {

    public static final int MAX_STATEMENTS = 4;

    private static final String SHAPES = "@click 只能写 x = 表达式、x++、x--、close()、back()、nav('页面')";

    sealed interface Stmt permits Assign, Step, Close, Back, Nav {
    }

    record Assign(String key, Expr.Compiled value) implements Stmt {
    }

    record Step(String key, int delta) implements Stmt {
    }

    record Close() implements Stmt {
    }

    record Back() implements Stmt {
    }

    record Nav(Expr.Compiled page) implements Stmt {
    }

    /**
     * 一次点击的结果。applied 为 false 时 state 一个都没改，close / back / nav 也都不算数。
     * close、back、nav 同时出现时由页面决定先后，这里只如实报出写了哪些。
     */
    public record Outcome(boolean applied, boolean close, boolean back, String nav, List<String> warnings) {
    }

    /** 实例化时绑好 v-for 变量的一条 @click，点击时执行。 */
    public record Bound(Statements statements, List<String> names, List<Object> values) {
        public Outcome run(UiState state, String file) {
            return statements.run(state, file, names, values);
        }
    }

    private final List<Stmt> list;
    private final int line;

    private Statements(List<Stmt> list, int line) {
        this.list = list;
        this.line = line;
    }

    List<Stmt> list() {
        return list;
    }

    static Statements parse(String src, Scope scope, int line, int col) {
        if (src.length() > MAX_STATEMENTS * (ExprParser.MAX_LENGTH + 1)) throw SfcError.at(Code.E_EXPR_TOO_LONG, line, col);
        ExprParser p = new ExprParser(src, line, col, false);
        if (p.peek().kind() == 'e') throw ExprParser.syntax(p.peek(), "@click 是空的");
        List<Stmt> out = new ArrayList<>();
        while (p.peek().kind() != 'e') {
            Tok first = p.peek();
            if (out.size() == MAX_STATEMENTS) throw ExprParser.syntax(first, "@click 最多 " + MAX_STATEMENTS + " 条语句");
            out.add(statement(p, scope, src));
            if (p.peek().offset() - first.offset() > ExprParser.MAX_LENGTH) {
                throw SfcError.at(Code.E_EXPR_TOO_LONG, first.line(), first.col());
            }
            if (p.at(";")) {
                p.next();
            } else if (p.peek().kind() != 'e') {
                throw ExprParser.syntax(p.peek(), "两条语句之间要用 ; 隔开");
            }
        }
        return new Statements(List.copyOf(out), line);
    }

    private static Stmt statement(ExprParser p, Scope scope, String src) {
        Tok t = p.next();
        if (t.kind() != 'n') throw ExprParser.syntax(t, SHAPES);
        Tok after = p.peek();
        String name = t.text();

        if (ExprParser.is(after, "(")) {
            switch (name) {
                case "close", "back" -> {
                    p.next();
                    p.expect(")");
                    return name.equals("close") ? new Close() : new Back();
                }
                case "nav" -> {
                    p.next();
                    Typed page = p.parse(scope);
                    p.expect(")");
                    return new Nav(compiled(page, t, src));
                }
                default -> throw SfcError.at(Code.E_EXPR_NO_CALLS, t.line(), t.col());
            }
        }

        if (ExprParser.is(after, "=") || ExprParser.is(after, "++") || ExprParser.is(after, "--")) {
            if (scope.isLoopVar(name)) throw ExprParser.syntax(t, "v-for 的变量 '" + name + "' 不能赋值，只能赋给 state 里的 key");
            if (!scope.isState(name)) throw ExprParser.unknownIdent(t, scope.names());
            p.next();
            if (ExprParser.is(after, "=")) return new Assign(name, compiled(p.parse(scope), t, src));
            return new Step(name, ExprParser.is(after, "++") ? 1 : -1);
        }
        if (after.kind() == 'p' && after.text().length() == 2 && after.text().charAt(1) == '=' && "+-*/%".indexOf(after.text().charAt(0)) >= 0) {
            throw ExprParser.syntax(after, "没有 " + after.text() + "，写成 " + name + " = " + name + " " + after.text().charAt(0) + " …");
        }
        throw ExprParser.syntax(t, SHAPES);
    }

    private static Expr.Compiled compiled(Typed t, Tok at, String src) {
        if (t.nodes() > ExprParser.MAX_NODES) throw SfcError.at(Code.E_EXPR_TOO_COMPLEX, at.line(), at.col(), t.nodes());
        return new Expr.Compiled(t.expr(), at.line(), src);
    }

    Outcome run(UiState state, String file, List<String> names, List<Object> values) {
        Map<String, Object> scratch = new LinkedHashMap<>(state.values());
        EvalContext c = new EvalContext(scratch, file);
        for (int i = 0; i < names.size(); i++) c.push(names.get(i), values.get(i));
        boolean close = false;
        boolean back = false;
        String nav = null;
        Set<String> changed = new LinkedHashSet<>();
        for (Stmt s : list) {
            c.line = line;
            if (s instanceof Assign a) {
                Object v = a.value().run(c);
                Object old = scratch.get(a.key());
                if (!Objects.equals(StateRules.kind(old), StateRules.kind(v))) {
                    return failed(c, a.key() + " 是 " + Values.kind(old) + "，不能写入 " + Values.kind(v));
                }
                scratch.put(a.key(), v);
                changed.add(a.key());
            } else if (s instanceof Step st) {
                if (!(scratch.get(st.key()) instanceof Integer n)) {
                    return failed(c, st.key() + (st.delta() > 0 ? "++" : "--") + " 只能用在 int 上，它是 " + Values.kind(scratch.get(st.key())));
                }
                scratch.put(st.key(), n + st.delta());
                changed.add(st.key());
            } else if (s instanceof Nav nv) {
                Object v = nv.page().run(c);
                if (!(v instanceof String page)) return failed(c, "nav(...) 要页面名字符串，得到的是 " + Values.kind(v));
                nav = page;
            } else if (s instanceof Close) {
                close = true;
            } else {
                back = true;
            }
        }
        for (String k : changed) state.set(k, scratch.get(k));
        return new Outcome(true, close, back, nav, c.warnings());
    }

    private static Outcome failed(EvalContext c, String reason) {
        c.warn(reason + "，这组 @click 一条都不执行");
        return new Outcome(false, false, false, null, c.warnings());
    }
}
