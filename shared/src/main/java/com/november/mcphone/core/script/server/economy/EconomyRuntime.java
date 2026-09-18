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
 * <p>注册表（有哪几种货币、各用哪一档）还没接进来，那是 S15f 的事；所以开服扫描时超时托管找不到 provider 退款，
 * 只计数不动账 —— 钱留在托管里，比退错地方强。
 */
public final class EconomyRuntime {

    /** 超时退款写进流水的 {@code reason.kind}。 */
    public static final String TIMEOUT_REFUND_KIND = "escrow_timeout";

    private static volatile EconomyRuntime current;

    private final EconomyData data;
    private final TxnLog log;
    private final CurrencyGateway gateway;

    private EconomyRuntime(EconomyData data, TxnLog log, CurrencyGateway gateway) {
        this.data = data;
        this.log = log;
        this.gateway = gateway;
    }

    /** 开服时在主线程上调。重复调会先把上一份关掉。 */
    public static synchronized void start(MinecraftServer server) {
        stop();
        EconomyData data = EconomyData.get(server);
        TxnLog log = new TxnLog(server.getWorldPath(LevelResource.ROOT).resolve("mcphone").resolve("economy"),
                ZoneId.systemDefault(), data);
        Instant now = Instant.now();
        log.noteRestart(now);
        data.onSave(() -> log.checkpoint(Instant.now()));
        log.sweep(now);
        Sweep s = sweepEscrow(data.escrow(), currencyId -> null);
        if (s.orphaned() > 0) {
            MCphone.LOGGER.warn("[MCphone] 有 {} 笔托管已超时，但那种货币还没注册，先不退 —— 钱仍在托管里", s.orphaned());
        }
        CurrencyGateway gateway = new CurrencyGateway(server::execute,
                () -> Thread.currentThread() == server.getRunningThread());
        gateway.open();
        current = new EconomyRuntime(data, log, gateway);
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
     * @param failed   provider 拒了几笔（比如那种货币的存档锁住了）—— 还在托管里，下次开服再试
     * @param orphaned 找不到 provider 的几笔 —— 没动
     * @param pruned   清掉了几条很久以前已结清的
     */
    public record Sweep(int refunded, int failed, int orphaned, int pruned) {
    }

    /**
     * 超时托管退回原主（§22.4，默认 7 天），并清掉很久以前已结清的条目。
     *
     * <p>退款走那种货币自己的 provider：钱要回到它原来的地方（builtin 的余额、计分板、外部钱包），
     * 流水也由它记。<b>不许绕过 provider 直接改账</b> —— 那样计分板档的钱会退进一本它根本不用的账里。
     */
    public static Sweep sweepEscrow(EscrowLedger escrow, Function<String, ICurrencyProvider> providers) {
        int refunded = 0, failed = 0, orphaned = 0;
        for (Map.Entry<EscrowId, EscrowLedger.Entry> e : escrow.expired()) {
            ICurrencyProvider p = providers.apply(e.getValue().currencyId());
            if (p == null) {
                orphaned++;
                continue;
            }
            TxnResult r = p.refund(e.getKey(), new TxnReason(TIMEOUT_REFUND_KIND, e.getKey().value().toString()));
            if (r == TxnResult.OK) refunded++;
            else failed++;
        }
        return new Sweep(refunded, failed, orphaned, escrow.pruneSettled());
    }
}
