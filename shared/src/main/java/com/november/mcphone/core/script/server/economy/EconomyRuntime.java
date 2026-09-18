package com.november.mcphone.core.script.server.economy;

import com.november.mcphone.MCphone;
import com.november.mcphone.api.economy.EscrowId;
import com.november.mcphone.api.economy.ICurrencyProvider;
import com.november.mcphone.api.economy.TxnReason;
import com.november.mcphone.api.economy.TxnResult;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;

/**
 * 货币这一摊跟着服务器生死（§15.5）。
 *
 * <p>开服：读存档 → 标出流水比存档超前的那截（存档点之后的） → 扫超时托管 → <b>最后才开网关</b>：扫描完成之前不接受任何货币调用。
 * 停服：先关网关，<b>要在停 worker 之前</b>（理由见 {@link CurrencyGateway#close()}）。
 *
 * <p>超时托管除了开服扫一次，运行中每 {@link #SWEEP_INTERVAL_MS} 再扫一次（{@link #tick()}）：只在开服扫，
 * 服务器跑得越久越不守 7 天的规则。
 *
 * <p>注册表（有哪几种货币、各用哪一档）还没接进来，那是 S15f 的事；所以扫描时超时托管找不到 provider 退款，
 * 只计数不动账 —— 钱留在托管里，比退错地方强。
 */
public final class EconomyRuntime {

    /** 超时退款写进流水的 {@code reason.kind}。 */
    public static final String TIMEOUT_REFUND_KIND = "escrow_timeout";

    /** 运行中扫超时托管的间隔。超时本身是 {@link EscrowLedger#DEFAULT_TIMEOUT_MS}（7 天），这里只管多久看一次。 */
    public static final long SWEEP_INTERVAL_MS = 5L * 60 * 1000;

    private static volatile EconomyRuntime current;

    private final EconomyData data;
    private final TxnLog log;
    private final CurrencyGateway gateway;
    /** 货币 id → 它的 provider。注册表接进来（S15f）之前一律找不到。 */
    private final Function<String, ICurrencyProvider> providers;
    private long nextSweepAt;
    private int lastOrphaned;
    private int lastFailed;

    EconomyRuntime(EconomyData data, TxnLog log, CurrencyGateway gateway,
                   Function<String, ICurrencyProvider> providers, long now) {
        this.data = data;
        this.log = log;
        this.gateway = gateway;
        this.providers = providers;
        this.nextSweepAt = now + SWEEP_INTERVAL_MS;
    }

    /** 开服时在主线程上调。重复调会先把上一份关掉。 */
    public static synchronized void start(MinecraftServer server) {
        stop();
        EconomyData data = EconomyData.get(server);
        // 整份锁住的存档不接进流水：它永远不写存档点，接上了流水就会替它自动补存档点、说它"存过了"
        TxnLog log = new TxnLog(server.getWorldPath(LevelResource.ROOT).resolve("mcphone").resolve("economy"),
                ZoneId.systemDefault(), data.wholeLock() == null ? data : null);
        Instant now = Instant.now();
        // 整份锁住时存档读不出来，流水比它超前多少无从谈起；而且锁住的存档永远不写存档点，报了每次开服都会重报
        if (data.wholeLock() == null) log.noteRestart(now);
        data.onSave(() -> log.checkpoint(Instant.now()));
        log.sweep(now);
        CurrencyGateway gateway = new CurrencyGateway(server::execute,
                () -> Thread.currentThread() == server.getRunningThread());
        EconomyRuntime r = new EconomyRuntime(data, log, gateway, currencyId -> null, System.nanoTime() / 1_000_000);
        r.sweepNow();
        gateway.open();
        current = r;
    }

    /**
     * 每个服务端 tick 结束时在主线程上调。到点才扫：间隔按单调时钟算 —— tick 数在服务器卡的时候不准，
     * 墙钟往回拨的话会停扫那么久。
     */
    public static void tick() {
        EconomyRuntime r = current;
        if (r != null) r.sweepIfDue(System.nanoTime() / 1_000_000);
    }

    /** 到点就扫一次。幂等：只退没结清、已到期的，退过的已经结清。{@code nowMonotonicMs} 只拿来比间隔。 */
    void sweepIfDue(long nowMonotonicMs) {
        if (nowMonotonicMs < nextSweepAt) return;
        nextSweepAt = nowMonotonicMs + SWEEP_INTERVAL_MS;
        sweepNow();
    }

    /**
     * 扫一趟，什么都不往外抛：开服时抛出去服务器就起不来；tick 里抛出去会穿过事件总线把服务器弄崩，
     * Fabric 上还会连带跳过同一个 tick 里的别的活。provider 抛的在 {@link #sweepEscrow} 里逐笔接住，这里接的是剩下的。
     */
    void sweepNow() {
        try {
            report(sweepEscrow(data.escrow(), providers));
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable e) {
            MCphone.LOGGER.error("[MCphone] 扫超时托管时出错，下次再扫", e);
        }
    }

    // 找不到 provider 的只在数目变了时说一次：每 5 分钟报同一句会把日志刷满
    private void report(Sweep s) {
        if (s.orphaned() != lastOrphaned && s.orphaned() > 0) {
            MCphone.LOGGER.warn("[MCphone] 有 {} 笔托管已超时，但那种货币还没注册，先不退 —— 钱仍在托管里", s.orphaned());
        }
        lastOrphaned = s.orphaned();
        if (s.refunded() > 0) MCphone.LOGGER.info("[MCphone] 超时托管：退了 {} 笔", s.refunded());
        // 被拒的每 5 分钟会再试一次、再被拒一次：同一个数只说一次，堆栈也只在这时候打
        if (s.failed() != lastFailed && s.failed() > 0) {
            if (s.error() == null) {
                MCphone.LOGGER.warn("[MCphone] 超时托管：{} 笔退款被拒（那种货币现在用不了），下次再试", s.failed());
            } else {
                MCphone.LOGGER.error("[MCphone] 超时托管：{} 笔退款没成（有 provider 抛了异常，下面是第一个），下次再试",
                        s.failed(), s.error());
            }
        }
        lastFailed = s.failed();
    }

    /** 停服时在主线程上调，<b>在 {@code ScriptWorkers.stop()} 之前</b>。 */
    public static synchronized void stop() {
        EconomyRuntime r = current;
        current = null;
        if (r != null) r.gateway.close();
    }

    /** 没开服、或者已经停了就是 null。 */
    public static EconomyRuntime current() {
        return current;
    }

    public EconomyData data() {
        return data;
    }

    public TxnLog log() {
        return log;
    }

    public CurrencyGateway gateway() {
        return gateway;
    }

    /**
     * @param refunded 退成了几笔
     * @param failed   provider 拒了或抛了几笔（比如那种货币的存档锁住了）—— 还在托管里，下次再试
     * @param orphaned 找不到 provider 的几笔 —— 没动
     * @param pruned   清掉了几条很久以前已结清的
     * @param error    第一笔抛出来的异常；没有是 null
     */
    public record Sweep(int refunded, int failed, int orphaned, int pruned, Throwable error) {
    }

    /**
     * 超时托管退回原主（§22.4，默认 7 天），并清掉很久以前已结清的条目。
     *
     * <p>退款走那种货币自己的 provider：钱要回到它原来的地方（builtin 的余额、计分板、外部钱包），
     * 流水也由它记。<b>不许绕过 provider 直接改账</b> —— 那样计分板档的钱会退进一本它根本不用的账里。
     */
    public static Sweep sweepEscrow(EscrowLedger escrow, Function<String, ICurrencyProvider> providers) {
        int refunded = 0, failed = 0, orphaned = 0;
        Throwable error = null;
        for (Map.Entry<EscrowId, EscrowLedger.Entry> e : escrow.expired()) {
            // 逐笔接住：一种货币的 provider 抛了，别的货币照样退、已结清的照样清
            try {
                ICurrencyProvider p = providers.apply(e.getValue().currencyId());
                if (p == null) {
                    orphaned++;
                    continue;
                }
                TxnResult r = p.refund(e.getKey(), new TxnReason(TIMEOUT_REFUND_KIND, e.getKey().value().toString()));
                if (r == TxnResult.OK) refunded++;
                else failed++;
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable ex) {
                // 不只接 RuntimeException：外部经济模组换了版本，抛的是 NoSuchMethodError 之类的 LinkageError
                failed++;
                if (error == null) error = ex;
            }
        }
        return new Sweep(refunded, failed, orphaned, escrow.pruneSettled(), error);
    }
}
