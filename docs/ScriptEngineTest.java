package com.november.mcphone.core.script.engine;

import com.november.mcphone.core.script.server.PlayerSnapshot;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 脚本沙箱与预算（施工方案 §16.3、§16.4、§16.6、§16.7、§15.2）。
 *
 * <p><b>这里测不了的</b>：死循环期间服务器 TPS、KubeJS 双端共存（V-4）、管理界面 ——
 * 都要一台真在跑的游戏。
 *
 * <p>跑法：{@code ./gradlew assertTests}（在 {@code platforms/<目标名>/} 下）。
 */
public class ScriptEngineTest {

    static int checks = 0;
    static final List<String> failures = new ArrayList<>();

    static void eq(Object actual, Object expected, String what) {
        checks++;
        if (!java.util.Objects.equals(actual, expected)) failures.add(what + "  期望 " + expected + "，实际 " + actual);
    }

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) failures.add(what);
    }

    static final ScriptBudget BUDGET = ScriptBudget.server();

    /** 跑一段脚本，返回结果或异常的简名。 */
    static String run(String src) {
        Context cx = BUDGET.enterContext();
        try {
            BUDGET.begin();
            return String.valueOf(Context.toString(cx.evaluateString(ScriptSandbox.harden(cx), src, "t", 1, null)));
        } catch (Throwable t) {
            return t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()).split("\n")[0];
        } finally {
            BUDGET.end();
            Context.exit();
        }
    }

    static boolean aborted(String src, ScriptAbort.Reason reason) {
        String r = run(src);
        return r.startsWith("ScriptAbort: " + reason);
    }

    // ================================================================ §16.3 逃逸

    static void escapes() {
        check(run("eval('42')").contains("not defined"), "eval 没了");
        check(run("new Function('return 42')()").contains("not defined"), "Function 没了");
        // §16.3 的配方照抄之后 Script 还在（实测 new Script('40+2')() → 42）。
        // 它拿不到 Java，但它是第三个运行时编译入口 —— 击穿了 §16.3 自己写的
        // 「运行的代码可以和 OP 审的源码不是一回事」那条理由
        check(run("new Script('40+2')()").contains("not defined"), "Script 没了 —— 第三个运行时编译入口");
        // 判据是「拿不到 42」，不是错误文案长什么样：同一条链在不同写法下报的话不一样
        // （实测有 "Cannot find function constructor" 也有 "return 42 is not a function"）
        for (String chain : new String[]{
                "var f=function(){}; f.constructor('return 42')()",
                "[].constructor.constructor('return 42')()",
                "({}).constructor.constructor('return 42')()",
                "''.constructor.constructor('return 42')()",
                "(0).constructor.constructor('return 42')()",
                "Object.getPrototypeOf(function(){}).constructor('return 42')()"}) {
            String got = run(chain);
            check(!got.equals("42"), "这条链必须断：" + chain + " → " + got);
        }
        eq(run("typeof java"), "undefined", "typeof java");
        eq(run("typeof Packages"), "undefined", "typeof Packages");
        eq(run("typeof JavaImporter"), "undefined", "typeof JavaImporter");
        eq(run("typeof ''.getClass"), "undefined", "getClass");
        eq(run("typeof this.getClass"), "undefined", "this.getClass");
    }

    /**
     * 全局清单逐字等于白名单。
     *
     * <p><b>这是唯一会在 Rhino 升版新增全局时响的东西</b>：点名删的名单每次升版都会漏一批，
     * 所以判据反过来写。
     */
    static void globalsExactly() {
        String src = "Object.getOwnPropertyNames(this).sort().join(',')";
        String got = run(src);
        TreeSet<String> actual = new TreeSet<>(List.of(got.split(",")));
        TreeSet<String> expected = new TreeSet<>(ScriptSandbox.ALLOWED_GLOBALS);
        eq(actual, expected, "加固后的全局清单必须逐字等于白名单");

        // 点名几个删掉的，读起来一眼知道防的是什么
        for (String gone : new String[]{"Script", "ArrayBuffer", "Int8Array", "DataView",
                "Promise", "escape", "unescape", "Continuation", "JavaException"}) {
            eq(run("typeof " + gone), "undefined", gone + " 必须没有");
        }
    }

    static void sealed() {
        check(run("Object.prototype.p=1; ({}).p").contains("sealed"), "Object.prototype 封了");
        check(run("Array.prototype.q=1; [].q").contains("sealed"), "Array.prototype 封了");
        check(run("String.prototype.trim=function(){return 'X'}; ' a '.trim()").contains("sealed"), "改内置封了");
        // §16.3 的 SEAL 只点了 10 个名字，Map 不在里面 —— 实测能污染
        check(run("Map.prototype.zz=1; new Map().zz").contains("sealed"), "Map.prototype 也要封");
        check(run("Set.prototype.zz=1; new Set().zz").contains("sealed"), "Set.prototype 也要封");
    }

    static void normalStillWorks() {
        eq(run("var f=function(x){return x*2}; f(21)"), "42", "变量与函数");
        eq(run("((x)=>x+1)(41)"), "42", "箭头函数");
        eq(run("[1,2,3].map(function(x){return x*2}).join(',')"), "2,4,6", "map/join");
        eq(run("JSON.stringify({a:[1,2]})"), "{\"a\":[1,2]}", "JSON");
        eq(run("Math.max(1,42)"), "42", "Math");
        eq(run("var actions={}; actions.c=function(){return 7}; actions.c()"), "7", "actions 注入");
        eq(run("'ab'.repeat(3)"), "ababab", "正常 repeat 不受影响");
        eq(run("new Map([['a',1]]).get('a')"), "1", "Map 能用");
        // E2 顺延项：BigInt 边界精度
        eq(run("(BigInt('9007199254740993')+1n).toString()"), "9007199254740994", "BigInt 精度无损");
        eq(run("(9007199254740993n * 2n).toString()"), "18014398509481986", "BigInt 乘法");
    }

    // ================================================================ §16.4 预算

    static void budget() {
        String loop = run("var i=0; while(true) i++;");
        check(loop.startsWith("ScriptAbort:"), "死循环被中断，实际 " + loop);

        // 中断信号必须是 Error：实测宿主抛 RuntimeException 时 finally{return} 会把它吞掉
        String swallowed = run("function g(){ try{ var i=0; while(true) i++; } finally { return 'FINALLY_WINS'; } } g()");
        check(swallowed.startsWith("ScriptAbort:"), "finally{return} 吞不掉中断，实际 " + swallowed);
        String caught = run("function g(){ try{ var i=0; while(true) i++; } catch(e){} return 'SWALLOWED'; } g()");
        check(caught.startsWith("ScriptAbort:"), "catch 也吞不掉，实际 " + caught);

        check(run("/^(a+)+$/.test('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaX')").startsWith("ScriptAbort:"), "正则最坏回溯被中断");
        check(run("function f(n){return n<=0?0:f(n-1)+1} f(100000)").contains("stack depth"), "递归撞栈深");
    }

    /** 预算只在分支点生效，大分配靠尺寸闸拦（§16.4 的三件事 ①②）。 */
    static void sizeGate() {
        check(aborted("'x'.repeat(100000000).length", ScriptAbort.Reason.SIZE), "repeat 放大被拦");
        check(aborted("new Array(100000000).join('')", ScriptAbort.Reason.SIZE), "join 放大被拦");
        check(aborted("var s='x';for(var i=0;i<30;i++)s+=s; s.indexOf('y')", ScriptAbort.Reason.SIZE),
                "rope 物化被拦 —— 实测 9 毫秒能打爆 256 MB 堆");
        check(aborted("var a=[];a.length=100000000; a.fill(1)", ScriptAbort.Reason.SIZE), "fill 放大被拦");
        eq(SizeGate.MAX_STRING, 64 * 1024, "§16.4 ① 的字符串上限");
        eq(SizeGate.MAX_ARRAY, 4096, "§16.4 ① 的数组上限");
    }

    /** 跨调用驻留：每次调用都在预算内，26 次就打爆堆（实测）。 */
    static void retention() {
        AppScope app = new AppScope("t:app", BUDGET, Map.of());
        Context cx = BUDGET.enterContext();
        try {
            BUDGET.begin();
            ScriptableObject scope = app.scope(cx);
            // 直接往 App scope 上放一个超限的字符串，模拟跨调用累积出来的那一份
            ScriptableObject.putProperty(scope, "g", "x".repeat((int) AppScope.MAX_RETAINED_CHARS + 1));
            check(app.sweepRetained(), "驻留超限要被扫出来");
        } finally {
            BUDGET.end();
            Context.exit();
        }
        Context cx2 = BUDGET.enterContext();
        try {
            BUDGET.begin();
            Object g = ScriptableObject.getProperty(app.scope(cx2), "g");
            check(!(g instanceof CharSequence), "scope 重建之后那一份没了");
        } finally {
            BUDGET.end();
            Context.exit();
        }
    }

    /** §16.7：两个 App 的 scope 互相看不见。 */
    static void scopesIsolated() {
        Context cx = BUDGET.enterContext();
        try {
            BUDGET.begin();
            Scriptable a = ScriptSandbox.harden(cx);
            Scriptable b = ScriptSandbox.harden(cx);
            cx.evaluateString(a, "var leak = 'A 的秘密';", "a", 1, null);
            Object seen = cx.evaluateString(b, "typeof leak", "b", 1, null);
            eq(Context.toString(seen), "undefined", "B 看不到 A 的全局");
        } catch (Throwable t) {
            failures.add("两个 scope 隔离测试抛了: " + t);
        } finally {
            BUDGET.end();
            Context.exit();
        }
    }

    // ================================================================ §16.5 / §16.7 的 ctx

    static PlayerSnapshot player() {
        return new PlayerSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "yumeka", "minecraft:overworld", "survival", 0);
    }

    static ItemView fakeItems() {
        return new ItemView() {
            public boolean matches(String handle, String predicate) {
                return true;
            }

            public String displayName(String handle) {
                return "铁剑";
            }

            public boolean isDamaged(String handle) {
                return false;
            }
        };
    }

    /** 在 ctx 在场的情况下跑一段。 */
    static String withCtx(String src, CtxBuilder.Backends backends) {
        Context cx = BUDGET.enterContext();
        try {
            BUDGET.begin();
            HostFn.resetDepth();
            ScriptableObject scope = ScriptSandbox.harden(cx);
            CtxBuilder.Result r = new CtxBuilder.Result();
            ScriptableObject ctx = CtxBuilder.build(cx, scope, "t:app", player(), backends, r);
            ScriptableObject.putProperty(scope, "ctx", ctx);
            return String.valueOf(Context.toString(cx.evaluateString(scope, src, "t", 1, null)));
        } catch (Throwable t) {
            return t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()).split("\n")[0];
        } finally {
            BUDGET.end();
            Context.exit();
        }
    }

    static final CtxBuilder.Backends FULL = new CtxBuilder.Backends(
            new SharedState(), fakeItems(),
            new CtxBuilder.Cycle(ZoneId.of("Asia/Shanghai"), LocalTime.of(4, 0)));

    /**
     * §16.7：ctx 上枚举不出表外的东西。
     *
     * <p>判据比原文严：<b>ctx 与它的每一级子对象</b>，三种枚举都只给表内名字，
     * 且 {@code getPrototypeOf} 恒为 null。
     */
    static void ctxEnumeration() {
        eq(withCtx("Object.getOwnPropertyNames(ctx).sort().join(',')", FULL),
                "cycle,fail,item,log,ok,player,shared,time", "ctx 顶层只有这些");
        eq(withCtx("var n=[];for(var k in ctx)n.push(k);n.sort().join(',')", FULL),
                "cycle,fail,item,log,ok,player,shared,time", "for..in 也只有这些");
        eq(withCtx("String(Object.getPrototypeOf(ctx))", FULL), "null", "ctx 没有原型链");

        for (String child : new String[]{"player", "time", "cycle", "shared", "item"}) {
            eq(withCtx("String(Object.getPrototypeOf(ctx." + child + "))", FULL),
                    "null", "ctx." + child + " 也没有原型链");
            eq(withCtx("typeof ctx." + child + ".getClass", FULL), "undefined", "ctx." + child + " 摸不到 getClass");
            eq(withCtx("typeof ctx." + child + ".constructor", FULL), "undefined", "ctx." + child + " 摸不到 constructor");
        }
        eq(withCtx("typeof ctx.getClass", FULL), "undefined", "ctx 摸不到 getClass");
        eq(withCtx("typeof ctx.equals", FULL), "undefined", "ctx 上没有 Java 的 equals");
        eq(withCtx("typeof ctx.wait", FULL), "undefined", "ctx 上没有 Java 的 wait");
        check(withCtx("ctx.evil=1; typeof ctx.evil", FULL).contains("sealed"), "ctx 封了，加不了属性");

        // 表里没有的一律没有（§32.7：store/currency/mailbox/fetch 的后端还没到货）
        for (String absent : new String[]{"store", "currency", "mailbox", "fetch", "give", "loot", "command"}) {
            eq(withCtx("typeof ctx." + absent, FULL), "undefined",
                    "ctx." + absent + " 本步没有后端，就不该挂出来");
        }
    }

    /** E2 顺延项：opaque 在脚本侧既解析不了也构造不了。 */
    static void ctxItemOpaque() {
        eq(withCtx("Object.getOwnPropertyNames(ctx.item).sort().join(',')", FULL),
                "displayName,isDamaged,matches", "ctx.item 只有 §23.3 允许的三个");
        eq(withCtx("typeof ctx.item.nbt", FULL), "undefined", "没有 nbt()");
        eq(withCtx("typeof ctx.item.enchantments", FULL), "undefined", "没有 enchantments()");
        eq(withCtx("typeof ctx.item.decode", FULL), "undefined", "没有解码口");
        eq(withCtx("typeof ctx.item.create", FULL), "undefined", "没有构造口");
        eq(withCtx("ctx.item.displayName('3f2504e0a1b2c3d4e5f60718293a4b5c')", FULL), "铁剑", "只拿得到显示名");
        // 句柄就是一串十六进制，从它身上什么都解不出来
        eq(withCtx("typeof '3f2504e0a1b2c3d4e5f60718293a4b5c'.nbt", FULL), "undefined", "句柄上没有任何解析方法");
    }

    static void ctxBasics() {
        eq(withCtx("ctx.player.uuid", FULL), "00000000-0000-0000-0000-000000000001", "player.uuid");
        eq(withCtx("ctx.player.name", FULL), "yumeka", "player.name");
        eq(withCtx("ctx.player.gameMode", FULL), "survival", "player.gameMode");
        eq(withCtx("typeof ctx.player.onlineSince", FULL), "undefined", "§32.7 没列 onlineSince");
        eq(withCtx("typeof ctx.time.epochMillis()", FULL), "string", "时间戳是十进制字符串，不是数字");
        eq(withCtx("ctx.cycle.label('daily').length", FULL), "10", "daily 标签是 yyyy-MM-dd");
        check(withCtx("ctx.cycle.label('yearly')", FULL).contains("daily/weekly/monthly"), "认不出的粒度要拒");

        eq(withCtx("ctx.shared.set('k','v'); ctx.shared.get('k')", FULL), "v", "shared 读写");
        eq(withCtx("ctx.shared.compareAndSet('n',null,'1')", FULL), "true", "CAS 建新键");
        eq(withCtx("ctx.shared.set('n','1'); String(ctx.shared.compareAndSet('n','2','3'))", FULL),
                "false", "expected 对不上就不换");
    }

    /** 宿主桥只收原语：不许靠 valueOf 回调重入宿主。 */
    static void noCoercionCallback() {
        String r = withCtx("var evil={valueOf:function(){return 'x'}}; ctx.shared.get(evil)", FULL);
        check(r.contains("要字符串"), "给对象要当场拒，不能去调它的 valueOf，实际 " + r);
        String r2 = withCtx("ctx.shared.get(123)", FULL);
        check(r2.contains("要字符串"), "给数字也拒，实际 " + r2);
    }

    // ================================================================ §15.2 require

    static void requireTable() {
        ScriptModules m = new ScriptModules(Map.of(
                "server.js", "x", "server/gift.js", "y", "lib/util.js", "z"));
        eq(m.size(), 3, "三个模块");
        eq(ScriptModules.normalize("", "./server/gift.js"), "server/gift.js", "同级");
        eq(ScriptModules.normalize("server", "./gift.js"), "server/gift.js", "子目录里的同级");
        eq(ScriptModules.normalize("server", "../lib/util.js"), "lib/util.js", "上一级");
        eq(ScriptModules.normalize("", "../etc/passwd"), null, "弹出包根要拒");
        eq(ScriptModules.normalize("server", "../../x.js"), null, "弹穿两级也拒");
        eq(ScriptModules.normalize("", "./a\\b.js"), null, "反斜杠拒");
        eq(ScriptModules.normalize("", "./C:/x.js"), null, "带盘符拒");
        eq(ScriptModules.normalize("", "./a/./b.js"), "a/b.js", "单点跳过");

        check(abortsWith(() -> m.require("server/gift.js", "server.js", (k, s) -> null), "只许包内相对路径"),
                "不带 ./ 的一律拒");
        check(abortsWith(() -> m.require("./nope.js", "server.js", (k, s) -> null), "找不到"), "表里没有就拒");

        // 缓存命中不计深度、不计模块数
        Object[] loaded = {0};
        Object first = m.require("./server/gift.js", "server.js", (k, s) -> {
            loaded[0] = (int) loaded[0] + 1;
            return "E";
        });
        Object second = m.require("./server/gift.js", "server.js", (k, s) -> {
            loaded[0] = (int) loaded[0] + 1;
            return "E2";
        });
        eq(first, "E", "第一次求值");
        eq(second, "E", "第二次走缓存");
        eq(loaded[0], 1, "只求值了一次");
        eq(m.depth(), 0, "栈平了");
    }

    static void requireCycle() {
        ScriptModules m = new ScriptModules(Map.of("a.js", "", "b.js", ""));
        check(abortsWith(() -> m.require("./a.js", "server.js",
                (k, s) -> m.require("./b.js", k, (k2, s2) -> m.require("./a.js", k2, (k3, s3) -> null))),
                "循环依赖"), "循环依赖要报出来");
    }

    static void requireLimits() {
        java.util.Map<String, String> many = new java.util.HashMap<>();
        for (int i = 0; i <= ScriptModules.MAX_MODULES; i++) many.put("m" + i + ".js", "");
        check(abortsWith(() -> new ScriptModules(many), "上限"), "模块数超限在建表时就拒");
        check(abortsWith(() -> new ScriptModules(Map.of("a.vue", "")), "非 .js"), "非 .js 拒");
    }

    static boolean abortsWith(Runnable body, String fragment) {
        try {
            body.run();
            return false;
        } catch (ScriptAbort e) {
            return e.getMessage().contains(fragment);
        }
    }

    // ================================================================ §16.6 禁用

    static void strikes() {
        AtomicLong t = new AtomicLong(0);
        StrikeTracker st = new StrikeTracker(t::get);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();

        check(st.allowed("app", a), "一开始允许");
        check(!st.recordAbort("app", a), "第 1 次不禁");
        check(!st.recordAbort("app", a), "第 2 次不禁");
        check(st.recordAbort("app", a), "第 3 次禁");
        check(!st.allowed("app", a), "被禁了");

        // 关键：禁的是这个玩家，不是整个 App。否则抢购开场谁都能把它对全服关掉
        check(st.allowed("app", b), "别的玩家不受影响 —— 这是主键选 (App, 玩家) 的全部理由");

        t.set(StrikeTracker.PLAYER_BAN_MS);
        check(st.allowed("app", a), "5 分钟后解禁");

        // "连续"：中间成功一次就清零
        StrikeTracker st2 = new StrikeTracker(new AtomicLong(0)::get);
        st2.recordAbort("app", a);
        st2.recordAbort("app", a);
        st2.recordOk("app", a);
        check(!st2.recordAbort("app", a), "成功一次之后重新数");
    }

    /**
     * 余额读不到（那种货币的存档锁住了、网关拒了）时 {@code ctx.currency.balance} 抛脚本接得住的 Error：
     * 不给 0 或 null（比大小时 null 也当 0，App 会告诉玩家他没钱），也不抛 ScriptAbort（接不住、记过失、会熔断整个 App）。
     */
    static void currencyBalanceUnavailable() {
        String busy = com.november.mcphone.core.script.server.economy.CurrencyGateway.KEY_BUSY;
        java.util.concurrent.atomic.AtomicInteger paid = new java.util.concurrent.atomic.AtomicInteger();
        // pay 成功、balance 被拒：真正要防的是"钱已经转了，求值却中断"
        var coin = new com.november.mcphone.api.economy.Currency(
                net.minecraft.resources.ResourceLocation.tryParse("myserver:coin"),
                net.minecraft.network.chat.Component.literal("coin"), "G", 0, null);
        var provider = (com.november.mcphone.api.economy.ICurrencyProvider) java.lang.reflect.Proxy.newProxyInstance(
                ScriptEngineTest.class.getClassLoader(),
                new Class<?>[]{com.november.mcphone.api.economy.ICurrencyProvider.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "currency" -> coin;
                    case "transfer" -> {
                        paid.incrementAndGet();
                        yield com.november.mcphone.api.economy.TxnResult.OK;
                    }
                    case "balance" -> throw new com.november.mcphone.core.script.server.economy.CurrencyUnavailableException(busy);
                    case "isAvailable", "allowNegative" -> false;
                    case "maxBalance" -> Long.MAX_VALUE;
                    case "unavailableReasonKey" -> busy;
                    case "toString" -> "fake-coin";
                    case "hashCode" -> 0;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        var reg = new com.november.mcphone.core.script.server.economy.CurrencyRegistry();
        reg.register(provider, true);
        var backends = new CtxBuilder.Backends(new SharedState(), fakeItems(),
                new CtxBuilder.Cycle(ZoneId.of("Asia/Shanghai"), LocalTime.of(4, 0)), null, null, reg);
        String to = "'00000000-0000-0000-0000-000000000002'";

        eq(withCtx("var r = ctx.currency.pay('myserver:coin', " + to + ", 5n);"
                        + "try { ctx.currency.format('myserver:coin', ctx.currency.balance('myserver:coin')); 'no' }"
                        + "catch (e) { r + '|' + e.message }", backends),
                "OK|UNAVAILABLE: " + busy, "pay 成功之后 balance 被拒：脚本接得住，能自己 ctx.fail");
        eq(withCtx("try { ctx.currency.balance('myserver:coin') < 5n } catch (e) { 'caught' }", backends),
                "caught", "拿去比大小之前就抛了，不会被当成 0");
        String uncaught = withCtx("ctx.currency.balance('myserver:coin')", backends);
        check(uncaught.startsWith("EcmaError"), "没接住时是脚本错误（不记过失），不是 ScriptAbort：" + uncaught);
        eq(paid.get(), 1, "pay 只执行了一次");
    }

    public static void main(String[] args) {
        escapes();
        currencyBalanceUnavailable();
        globalsExactly();
        sealed();
        normalStillWorks();
        budget();
        sizeGate();
        retention();
        scopesIsolated();
        ctxEnumeration();
        ctxItemOpaque();
        ctxBasics();
        noCoercionCallback();
        requireTable();
        requireCycle();
        requireLimits();
        strikes();

        System.out.println("断言 " + checks + " 条");
        if (!failures.isEmpty()) {
            System.out.println("失败 " + failures.size() + " 条：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }
}
