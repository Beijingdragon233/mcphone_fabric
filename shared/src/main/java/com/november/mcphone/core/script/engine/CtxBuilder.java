package com.november.mcphone.core.script.engine;

import com.november.mcphone.api.sdk.cycle.CycleKind;
import com.november.mcphone.api.sdk.cycle.CycleLabels;
import com.november.mcphone.core.script.net.ScriptErrorCode;
import com.november.mcphone.core.script.server.store.KvBackend;
import com.november.mcphone.core.script.server.store.SealedBackend;
import com.november.mcphone.core.script.server.store.SealedRecord;
import com.november.mcphone.core.script.server.store.StoreQuota;
import com.november.mcphone.api.economy.Balances;
import com.november.mcphone.api.economy.EscrowId;
import com.november.mcphone.api.economy.HoldResult;
import com.november.mcphone.api.economy.ICurrencyProvider;
import com.november.mcphone.api.economy.TxnReason;
import com.november.mcphone.api.economy.TxnResult;
import com.november.mcphone.core.script.server.economy.Amounts;
import com.november.mcphone.core.script.server.economy.CurrencyRegistry;
import com.november.mcphone.core.script.server.PlayerSnapshot;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 建 {@code ctx}（施工方案 §16.5 的表面、§32.7 的 P1 交付集）。
 *
 * <h2>只挂有后端的，不挂空壳</h2>
 *
 * §32.7 的 plain 档里，{@code ctx.store} / {@code ctx.sealed} / {@code ctx.quota} 是 S14 的，
 * {@code ctx.currency} 是 S15 的，{@code ctx.mailbox} 只有契约没有实现，
 * {@code ctx.predicate} 是 S18 的，{@code ctx.fetch} 是 S24 的。
 *
 * <p><b>没后端的一律不挂属性</b>，不挂一个返回 UNAVAILABLE 的壳。理由：
 * §16.7 判的是"{@code ctx} 上枚举不出表外的方法"——那是个<b>负向</b>判据，少挂不违反它；
 * 而多挂一个空壳会让 S14/S15 的实现者以为"授权审查在接口定下来的时候做过了"。
 * <b>每一个暴露出去的方法都是一次要重做的授权判定。</b>
 *
 * <h2>每一级都是手工建的对象</h2>
 *
 * 实测：把 Java 对象直接注入（{@code NativeJavaObject}）时，若为了让它能用而放行类访问，
 * 脚本 {@code ctx.player.getClass().getClassLoader()} 就能拿到 AppClassLoader。
 * 所以 {@code ctx} 与它下面的每一级都 {@code setPrototype(null)} + {@code setParentScope(null)} + 密封，
 * 值只许是 JS 原语、手工建的对象、或 {@link HostFn} 建的函数。
 */
public final class CtxBuilder {

    private CtxBuilder() {
    }

    /** 周期配置。真正的来源是 {@code mcphone-server.toml} 的 {@code [cycle]}（S14）。 */
    public record Cycle(ZoneId zone, LocalTime dailyAt) {
    }

    /** 能接上的后端。为 null 的那一项<b>整个不挂</b>。 */
    public record Backends(SharedState shared, ItemView item, Cycle cycle,
                           KvBackend store, SealedBackend sealed, CurrencyRegistry currencies) {

        /** 只有 S13 那几样的旧写法。 */
        public Backends(SharedState shared, ItemView item, Cycle cycle) {
            this(shared, item, cycle, null, null, null);
        }

        /** S14 那一版。 */
        public Backends(SharedState shared, ItemView item, Cycle cycle,
                        KvBackend store, SealedBackend sealed) {
            this(shared, item, cycle, store, sealed, null);
        }
    }

    /** 脚本调 {@code ctx.ok} / {@code ctx.fail} 之后落在这里。 */
    public static final class Result {
        public ScriptErrorCode code;
        public String messageKey = "";
        public List<String> messageArgs = List.of();
        public String dataJson = "";
        public final java.util.List<String> logs = new java.util.ArrayList<>();
    }

    private static final AtomicLong SEQ = new AtomicLong();

    /** 建一个 {@code ctx}。{@code result} 由调用方持有，求值结束后读它。 */
    public static ScriptableObject build(Context cx, Scriptable scope, String appId,
                                         PlayerSnapshot player, Backends backends, Result result) {
        ScriptableObject ctx = HostFn.obj(cx, scope);

        // ---- ctx.player：四个字段（§32.7），都是 JS 字符串，不是 Java 对象
        ScriptableObject p = HostFn.obj(cx, scope);
        ScriptableObject.putProperty(p, "uuid", player.uuid().toString());
        ScriptableObject.putProperty(p, "name", player.name());
        ScriptableObject.putProperty(p, "dimension", player.dimension());
        ScriptableObject.putProperty(p, "gameMode", player.gameMode());
        p.sealObject();
        ScriptableObject.putProperty(ctx, "player", p);

        // ---- ctx.time
        ScriptableObject time = HostFn.obj(cx, scope);
        HostFn.put(time, scope, "epochMillis", 0, (c, s, a) -> String.valueOf(System.currentTimeMillis()));
        HostFn.put(time, scope, "monotonicNanos", 0, (c, s, a) -> String.valueOf(System.nanoTime()));
        HostFn.put(time, scope, "seq", 0, (c, s, a) -> String.valueOf(SEQ.incrementAndGet()));
        time.sealObject();
        ScriptableObject.putProperty(ctx, "time", time);

        // ---- ctx.cycle（§23.3）：标签就是 §20.1 limit 守卫的 label
        if (backends.cycle() != null) {
            Cycle cfg = backends.cycle();
            ScriptableObject cycle = HostFn.obj(cx, scope);
            HostFn.put(cycle, scope, "label", 1, (c, s, a) -> {
                CycleKind k = CycleKind.of(HostFn.str(a, 0, "cycle.label"));
                if (k == null) throw new ScriptAbort(ScriptAbort.Reason.HOST, "cycle.label 只认 daily/weekly/monthly");
                return CycleLabels.label(k, Instant.now(), cfg.zone(), cfg.dailyAt());
            });
            HostFn.put(cycle, scope, "nextBoundary", 1, (c, s, a) -> {
                CycleKind k = CycleKind.of(HostFn.str(a, 0, "cycle.nextBoundary"));
                if (k == null) throw new ScriptAbort(ScriptAbort.Reason.HOST, "cycle.nextBoundary 只认 daily/weekly/monthly");
                // 十进制字符串：毫秒时间戳超过 2^53，用数字会静默丢精度（§23.3）
                return String.valueOf(CycleLabels.nextBoundary(k, Instant.now(), cfg.zone(), cfg.dailyAt()));
            });
            cycle.sealObject();
            ScriptableObject.putProperty(ctx, "cycle", cycle);
        }

        // ---- ctx.shared（§32.7 的 plain 档，限量竞争的唯一原语）
        if (backends.shared() != null) {
            SharedState st = backends.shared();
            ScriptableObject shared = HostFn.obj(cx, scope);
            HostFn.put(shared, scope, "get", 1, (c, s, a) -> {
                String v = st.get(appId, HostFn.str(a, 0, "shared.get"));
                return v == null ? null : v;
            });
            HostFn.put(shared, scope, "set", 2, (c, s, a) -> {
                st.set(appId, HostFn.str(a, 0, "shared.set"), HostFn.str(a, 1, "shared.set"));
                return Boolean.TRUE;
            });
            HostFn.put(shared, scope, "compareAndSet", 3, (c, s, a) -> {
                String key = HostFn.str(a, 0, "shared.compareAndSet");
                String expected = HostFn.present(a, 1) ? HostFn.str(a, 1, "shared.compareAndSet") : null;
                String next = HostFn.str(a, 2, "shared.compareAndSet");
                return st.compareAndSet(appId, key, expected, next);
            });
            shared.sealObject();
            ScriptableObject.putProperty(ctx, "shared", shared);
        }

        // ---- ctx.item（§23.3）：句柄只搬运，不解析也不构造
        if (backends.item() != null) {
            ItemView iv = backends.item();
            ScriptableObject item = HostFn.obj(cx, scope);
            HostFn.put(item, scope, "matches", 2, (c, s, a) ->
                    iv.matches(HostFn.str(a, 0, "item.matches"), HostFn.str(a, 1, "item.matches")));
            HostFn.put(item, scope, "displayName", 1, (c, s, a) ->
                    iv.displayName(HostFn.str(a, 0, "item.displayName")));
            HostFn.put(item, scope, "isDamaged", 1, (c, s, a) ->
                    iv.isDamaged(HostFn.str(a, 0, "item.isDamaged")));
            item.sealObject();
            ScriptableObject.putProperty(ctx, "item", item);
        }

        // ---- ctx.store（§16.5、§17.3）：每玩家的 KV。档位由"跑在哪一侧"决定，不在方法名里
        if (backends.store() != null) {
            KvBackend kv = backends.store();
            ScriptableObject store = HostFn.obj(cx, scope);
            HostFn.put(store, scope, "getString", 2, (c, s, a) -> {
                String v = kv.getString(appId, HostFn.str(a, 0, "store.getString"));
                return v != null ? v : (HostFn.present(a, 1) ? a[1] : null);
            });
            HostFn.put(store, scope, "setString", 2, (c, s, a) -> {
                kv.setString(appId, HostFn.str(a, 0, "store.setString"), HostFn.str(a, 1, "store.setString"));
                return Boolean.TRUE;
            });
            // 数值一律按十进制字符串过：毫秒时间戳与计数会超过 2^53（§23.3 同一条理由）
            HostFn.put(store, scope, "getLong", 2, (c, s, a) -> {
                String v = kv.getString(appId, HostFn.str(a, 0, "store.getLong"));
                return v != null ? v : (HostFn.present(a, 1) ? a[1] : "0");
            });
            HostFn.put(store, scope, "setLong", 2, (c, s, a) -> {
                kv.setString(appId, HostFn.str(a, 0, "store.setLong"),
                        Long.toString(HostFn.num(a, 1, "store.setLong")));
                return Boolean.TRUE;
            });
            HostFn.put(store, scope, "getBool", 2, (c, s, a) -> {
                String v = kv.getString(appId, HostFn.str(a, 0, "store.getBool"));
                return v == null ? (HostFn.present(a, 1) && HostFn.bool(a, 1, "store.getBool")) : "true".equals(v);
            });
            HostFn.put(store, scope, "setBool", 2, (c, s, a) -> {
                kv.setString(appId, HostFn.str(a, 0, "store.setBool"),
                        Boolean.toString(HostFn.bool(a, 1, "store.setBool")));
                return Boolean.TRUE;
            });
            HostFn.put(store, scope, "remove", 1, (c, s, a) -> {
                kv.remove(appId, HostFn.str(a, 0, "store.remove"));
                return Boolean.TRUE;
            });
            HostFn.put(store, scope, "keys", 0, (c, s, a) ->
                    c.newArray(s, kv.keys(appId).toArray()));
            store.sealObject();
            ScriptableObject.putProperty(ctx, "store", store);
        }

        // ---- ctx.sealed（§17.4.5）：只有 put 与 get，服务端只搬字节、解不开
        if (backends.sealed() != null) {
            SealedBackend sb = backends.sealed();
            ScriptableObject sealed = HostFn.obj(cx, scope);
            HostFn.put(sealed, scope, "put", 2, (c, s, a) -> {
                // 脚本递过来的是客户端封好的密文（base64）。宿主不解、也解不开
                throw new ScriptAbort(ScriptAbort.Reason.HOST,
                        "sealed.put 要由客户端把封好的记录递进来，S14 只定了后端形状");
            });
            HostFn.put(sealed, scope, "get", 1, (c, s, a) -> {
                SealedRecord r = sb.get(appId, HostFn.str(a, 0, "sealed.get"));
                return r == null ? null : java.util.Base64.getEncoder().encodeToString(r.cipher());
            });
            sealed.sealObject();
            ScriptableObject.putProperty(ctx, "sealed", sealed);
        }

        // ---- ctx.currency（§22.5）。金额进出都是 BigInt（勘误 E18），宿主在边界切 BigInt ↔ long
        // 【被拒的调用、玩家输错的数据是返回值，不中断】（S15h）：中断记过失，连着几次禁玩家、熔断整个 App ——
        // 那是在罚玩家。只有脚本自己写错（类型不对）才中断
        if (backends.currencies() != null) {
            CurrencyRegistry reg = backends.currencies();
            ScriptableObject cur = HostFn.obj(cx, scope);

            // App 不许写死货币 id（§22.8）：没有默认货币时【返回 null，不抛】，App 该 ctx.fail 而不是崩
            HostFn.put(cur, scope, "default", 0, (c, s, a) -> reg.defaultCurrency());
            HostFn.put(cur, scope, "list", 0, (c, s, a) -> {
                java.util.List<Object> out = new java.util.ArrayList<>();
                for (var m : reg.list()) {
                    ScriptableObject o = HostFn.obj(c, s);
                    ScriptableObject.putProperty(o, "id", m.id().toString());
                    ScriptableObject.putProperty(o, "symbol", m.symbol());
                    ScriptableObject.putProperty(o, "decimals", m.decimals());
                    o.sealObject();
                    out.add(o);
                }
                return c.newArray(s, out.toArray());
            });

            // balance 只能读自己（§22.5）。读别人是 currency.read.other，granted 档，本步不给
            HostFn.put(cur, scope, "balance", 1, (c, s, a) -> {
                ICurrencyProvider prov = requireOrError(reg, HostFn.str(a, 0, "currency.balance"));
                try {
                    return Amounts.toScript(prov.balance(player.uuid()));
                } catch (com.november.mcphone.core.script.server.economy.CurrencyUnavailableException e) {
                    // 抛脚本接得住的 Error，App 在 catch 里 ctx.fail('UNAVAILABLE')。
                    // 不返回 0 或 null：比大小时 null 也当 0，App 会告诉玩家他没钱。
                    // 不抛 ScriptAbort：那个接不住、还记过失，连着几次就把整个 App 熔断
                    throw org.mozilla.javascript.ScriptRuntime.constructError("Error", "UNAVAILABLE: " + e.reasonKey());
                }
            });

            // format 必须用宿主（§22.5）：自己拼小数点，负数与不足位就各错各的
            HostFn.put(cur, scope, "format", 2, (c, s, a) -> {
                ICurrencyProvider prov = requireOrError(reg, HostFn.str(a, 0, "currency.format"));
                long v = Amounts.toLong(a.length > 1 ? a[1] : null, "currency.format");
                return Balances.format(v, prov.currency().decimals()) + " " + prov.currency().symbol();
            });

            // parse 收字符串，回 BigInt
            HostFn.put(cur, scope, "parse", 2, (c, s, a) -> {
                ICurrencyProvider prov = requireOrError(reg, HostFn.str(a, 0, "currency.parse"));
                try {
                    return Amounts.toScript(Balances.parse(HostFn.str(a, 1, "currency.parse"),
                            prov.currency().decimals()));
                } catch (NumberFormatException e) {
                    // 解析的多半是玩家输入：解析不了给 null，App 该 ctx.fail('INVALID')
                    return null;
                }
            });

            // pay 的 from 恒为调用者（§22.6：这样它才是 plain 档）
            HostFn.put(cur, scope, "pay", 4, (c, s, a) -> {
                ICurrencyProvider prov = reg.get(HostFn.str(a, 0, "currency.pay"));
                if (prov == null) return TxnResult.UNAVAILABLE.name();
                java.util.UUID to = uuidOrNull(HostFn.str(a, 1, "currency.pay"));
                Long amt = amountOrNull(a, 2, "currency.pay");
                TxnReason why = reasonOrNull(a, 3, "pay");
                if (to == null || amt == null || why == null) return TxnResult.INVALID.name();
                return prov.transfer(player.uuid(), to, amt, why).name();
            });

            HostFn.put(cur, scope, "hold", 4, (c, s, a) -> {
                ICurrencyProvider prov = reg.get(HostFn.str(a, 0, "currency.hold"));
                if (prov == null) return TxnResult.UNAVAILABLE.name();
                java.util.UUID to = uuidOrNull(HostFn.str(a, 1, "currency.hold"));
                Long amt = amountOrNull(a, 2, "currency.hold");
                TxnReason why = reasonOrNull(a, 3, "hold");
                if (to == null || amt == null || why == null) return TxnResult.INVALID.name();
                HoldResult h = prov.hold(player.uuid(), to, amt, why);
                return h.result() == TxnResult.OK ? h.id().value().toString() : h.result().name();
            });

            HostFn.put(cur, scope, "release", 3, (c, s, a) -> {
                ICurrencyProvider prov = reg.get(HostFn.str(a, 0, "currency.release"));
                if (prov == null) return TxnResult.UNAVAILABLE.name();
                java.util.UUID id = uuidOrNull(HostFn.str(a, 1, "currency.release"));
                if (id == null) return TxnResult.UNKNOWN_ESCROW.name();
                TxnReason why = reasonOrNull(a, 2, "release");
                if (why == null) return TxnResult.INVALID.name();
                return prov.release(new EscrowId(id), why).name();
            });

            HostFn.put(cur, scope, "refund", 3, (c, s, a) -> {
                ICurrencyProvider prov = reg.get(HostFn.str(a, 0, "currency.refund"));
                if (prov == null) return TxnResult.UNAVAILABLE.name();
                java.util.UUID id = uuidOrNull(HostFn.str(a, 1, "currency.refund"));
                if (id == null) return TxnResult.UNKNOWN_ESCROW.name();
                TxnReason why = reasonOrNull(a, 2, "refund");
                if (why == null) return TxnResult.INVALID.name();
                return prov.refund(new EscrowId(id), why).name();
            });

            // mint / burn 是 granted 档（§22.6）。本步没有能力表，一律 NOT_AUTHORIZED ——
            // 【不静默降级】：没批就明说没批，别让 App 以为成功了
            for (String granted : new String[]{"mint", "burn"}) {
                HostFn.put(cur, scope, granted, 3, (c, s, a) -> TxnResult.NOT_AUTHORIZED.name());
            }

            cur.sealObject();
            ScriptableObject.putProperty(ctx, "currency", cur);
        }

        // ---- ctx.ok / ctx.fail / ctx.log
        HostFn.put(ctx, scope, "ok", 1, (c, s, a) -> {
            result.code = ScriptErrorCode.OK;
            result.dataJson = HostFn.present(a, 0) ? json(c, s, a[0]) : "";
            return Boolean.TRUE;
        });
        HostFn.put(ctx, scope, "fail", 3, (c, s, a) -> {
            String codeName = HostFn.str(a, 0, "ctx.fail");
            result.code = parse(codeName);
            result.dataJson = HostFn.present(a, 1) ? json(c, s, a[1]) : "";
            // messageKey 是本地化键，不是文本（§15.3）。键必须在 App 自己的 lang/*.json 里
            result.messageKey = HostFn.present(a, 2) ? HostFn.str(a, 2, "ctx.fail") : result.code.defaultMessageKey();
            return Boolean.TRUE;
        });
        HostFn.put(ctx, scope, "log", 1, (c, s, a) -> {
            if (result.logs.size() < 32) result.logs.add(HostFn.str(a, 0, "ctx.log"));
            return Boolean.TRUE;
        });

        ctx.sealObject();
        return ctx;
    }

    /** 认不出的货币 id 当场中断，不返回一个"看着像成功"的东西。 */
    /** 服务器上没有这种货币（多半是服主改了配置）：抛脚本接得住的 Error，不中断。 */
    private static ICurrencyProvider requireOrError(CurrencyRegistry reg, String id) {
        ICurrencyProvider p = reg.get(id);
        if (p == null) {
            throw org.mozilla.javascript.ScriptRuntime.constructError("Error", "UNAVAILABLE: " + NO_SUCH_CURRENCY);
        }
        return p;
    }

    static final String NO_SUCH_CURRENCY = "mcphone.economy.no_such_currency";

    /** 玩家、托管号是字符串，多半从别处传来：写歪了是返回码，不是脚本的错。 */
    private static java.util.UUID uuidOrNull(String s) {
        try {
            return java.util.UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 金额：不是 BigInt 是脚本写错了类型，照旧中断；是 BigInt 但超出 long 多半是玩家给的数，返回 null → INVALID。 */
    private static Long amountOrNull(Object[] args, int i, String where) {
        Object v = args.length > i ? args[i] : null;
        if (v instanceof java.math.BigInteger b && b.bitLength() > 63) return null;
        return Amounts.toLong(v, where);
    }

    /** ref 里有竖线、控制字符或太长（多半是把玩家输入当单号）：返回 null → INVALID。 */
    private static TxnReason reasonOrNull(Object[] args, int i, String kind) {
        try {
            return reason(args, i, kind);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@code reason} 里没有 appId 这一格 —— 由宿主盖章（§22.10）。 */
    private static TxnReason reason(Object[] args, int i, String kind) {
        String ref = HostFn.present(args, i) ? HostFn.str(args, i, "currency." + kind) : "";
        return new TxnReason(kind, ref);
    }

    /** 认不出的码一律 INTERNAL —— 不让脚本自己编一个码出来。 */
    private static ScriptErrorCode parse(String name) {
        for (ScriptErrorCode c : ScriptErrorCode.values()) {
            if (c.name().equals(name)) return c;
        }
        return ScriptErrorCode.INTERNAL;
    }

    /** 走沙箱里那个已经带了尺寸闸的 JSON.stringify。 */
    private static String json(Context cx, Scriptable scope, Object value) {
        Object out = org.mozilla.javascript.NativeJSON.stringify(cx, scope, value, null, "");
        if (out instanceof CharSequence cs) {
            SizeGate.check(cs, "ctx 的 data");
            return cs.toString();
        }
        return "";
    }
}
