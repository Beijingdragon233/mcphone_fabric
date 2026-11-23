package com.november.mcphone.core.script.engine;

import com.november.mcphone.core.script.net.ScriptErrorCode;
import com.november.mcphone.core.script.server.PlayerSnapshot;
import com.november.mcphone.internal.rhino.javascript.Context;
import com.november.mcphone.internal.rhino.javascript.ScriptableObject;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.TreeSet;
import java.util.UUID;

/** Executes the actual host, sandbox and budgets on the production Java 8 VM. */
public final class Java8ScriptRuntimeTest {
    private static int checks;
    private static void check(boolean ok, String label) {
        checks++;
        if (!ok) throw new AssertionError(label);
    }

    private static Object evaluate(String source, ScriptBudget budget, CtxBuilder.Result result,
                                   CtxBuilder.Backends backends, CtxBuilder.CapabilityGate gate) {
        try (Context cx = budget.enterContext()) {
            ScriptableObject scope = ScriptSandbox.harden(cx);
            ScriptableObject ctx = CtxBuilder.build(cx, scope, "test:java8",
                    new PlayerSnapshot(new UUID(0, 1), "Java8", "minecraft:overworld", "creative", 0),
                    backends, result, new MoneyLedger(), gate);
            ScriptableObject.putProperty(scope, "ctx", ctx);
            budget.begin();
            try { return cx.evaluateString(scope, source, "java8-runtime", 1, null); }
            finally { budget.end(); HostFn.resetDepth(); }
        }
    }

    public static void main(String[] args) {
        check("1.8".equals(System.getProperty("java.specification.version")), "must execute on Java 8");
        SharedState shared = new SharedState();
        CtxBuilder.Backends backends = new CtxBuilder.Backends(shared, null,
                new CtxBuilder.Cycle(ZoneId.of("UTC"), LocalTime.MIDNIGHT));
        CtxBuilder.Result result = new CtxBuilder.Result();
        ScriptBudget ordinary = new ScriptBudget(500_000, 1_000_000_000L);
        Object answer = evaluate("ctx.shared.set('answer','42');ctx.shared.get('answer')", ordinary, result,
                backends, CtxBuilder.CapabilityGate.ALLOW_ALL);
        check("42".equals(Context.toString(answer)), "real shared host round trip");
        check("42".equals(shared.get("test:java8", "answer")), "host writes persisted in backend");
        CtxBuilder.CapabilityGate deny = id -> { throw HostError.denied(ScriptErrorCode.NOT_AUTHORIZED, "denied", id); };
        try {
            evaluate("ctx.shared.set('answer','changed')", ordinary, new CtxBuilder.Result(), backends, deny);
            throw new AssertionError("permission denial missing");
        } catch (HostError expected) { check(expected.resultCode() == ScriptErrorCode.NOT_AUTHORIZED, "typed permission denial"); }
        check("42".equals(shared.get("test:java8", "answer")), "denial has no side effect");
        for (String expression : new String[]{"typeof java", "typeof Packages", "typeof eval",
                "typeof Function", "typeof this.getClass", "typeof (function(){}).constructor"}) {
            check("undefined".equals(Context.toString(evaluate(expression, ordinary, new CtxBuilder.Result(),
                    backends, CtxBuilder.CapabilityGate.ALLOW_ALL))), "sandbox: " + expression);
        }
        Object names = evaluate("Object.getOwnPropertyNames(this).filter(function(n){return n!=='ctx'}).sort().join(',')",
                ordinary, new CtxBuilder.Result(), backends, CtxBuilder.CapabilityGate.ALLOW_ALL);
        TreeSet<String> actual = new TreeSet<>();
        Collections.addAll(actual, Context.toString(names).split(","));
        check(actual.equals(new TreeSet<>(ScriptSandbox.ALLOWED_GLOBALS)), "full global whitelist");
        check("AggregateError: boom/2".equals(Context.toString(evaluate(
                "var e=new AggregateError([1,2],'boom');String(e)+'/'+e.errors.length", ordinary,
                new CtxBuilder.Result(), backends, CtxBuilder.CapabilityGate.ALLOW_ALL))), "AggregateError compatibility");
        for (String source : new String[]{"while(true){}", "try{while(true){}}catch(e){}finally{returned=1}"}) {
            try {
                evaluate(source, new ScriptBudget(10_000, 1_000_000_000L), new CtxBuilder.Result(), backends,
                        CtxBuilder.CapabilityGate.ALLOW_ALL);
                throw new AssertionError("instruction budget escaped");
            } catch (ScriptAbort expected) { check(expected.reason() == ScriptAbort.Reason.INSTRUCTIONS, "instruction abort cannot be swallowed"); }
        }
        try {
            evaluate("while(true){}", new ScriptBudget(Long.MAX_VALUE, 1), new CtxBuilder.Result(), backends,
                    CtxBuilder.CapabilityGate.ALLOW_ALL);
            throw new AssertionError("wall budget escaped");
        } catch (ScriptAbort expected) { check(expected.reason() == ScriptAbort.Reason.WALL_CLOCK, "wall clock abort"); }
        check("12".equals(Context.toString(evaluate("String(10n+2n)", ordinary, new CtxBuilder.Result(), backends,
                CtxBuilder.CapabilityGate.ALLOW_ALL))), "budget cleanup and BigInt");
        System.out.println("Java8ScriptRuntimeTest: " + checks + " checks passed on " + System.getProperty("java.runtime.version"));
    }
}
