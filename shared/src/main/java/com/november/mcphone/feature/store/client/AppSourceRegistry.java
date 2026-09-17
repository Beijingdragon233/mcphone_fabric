package com.november.mcphone.feature.store.client;

import com.november.mcphone.MCphone;
import com.november.mcphone.api.client.store.AppInfo;
import com.november.mcphone.api.client.store.IAppSource;
import com.november.mcphone.util.SpiLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** 应用来源注册表，通过 SPI 发现所有 {@link IAppSource} */
public final class AppSourceRegistry {

    private static final Map<ResourceLocation, IAppSource> SOURCES = new LinkedHashMap<>();
    private static boolean loaded = false;

    private AppSourceRegistry() {}

    /** 同 id 冲突时保留先注册者并告警 */
    public static boolean register(IAppSource source) {
        if (source == null || source.getId() == null) {
            MCphone.LOGGER.warn("[MCphone] 应用来源注册失败: id 为空");
            return false;
        }
        IAppSource old = SOURCES.get(source.getId());
        if (old != null) {
            MCphone.LOGGER.warn("[MCphone] 应用来源 id 冲突: '{}' 已由 {} 注册，忽略 {}",
                    source.getId(), old.getClass().getName(), source.getClass().getName());
            return false;
        }
        SOURCES.put(source.getId(), source);
        MCphone.LOGGER.info("[MCphone] 应用来源已注册: {}", source.getId());
        return true;
    }

    /** 全部已注册来源，保持注册顺序 */
    public static List<IAppSource> getSources() {
        ensureLoaded();
        return List.copyOf(SOURCES.values());
    }

    public static IAppSource getSource(ResourceLocation id) {
        ensureLoaded();
        return SOURCES.get(id);
    }

    /**
     * 等一个来源最多等这么久。到点就按已经回来的那些出列表。
     *
     * <p>取 10 秒：服务器商店那条路要等服务端回包（§14.1），几百毫秒是常态、网络差时几秒也有；
     * 而玩家盯着一张空列表超过十秒就会以为坏了。宁可少列几个来源的东西，也不能永远出不来。
     */
    public static final long LIST_TIMEOUT_MS = 10_000L;

    /**
     * 各来源可能异步返回，全部到齐后才调用 callback 一次；callback 在客户端主线程执行。
     *
     * <p><b>超时与恰好一次</b>（§14.1 ⚠、§14.6）：一个来源不回调、或者回调两次，原来都会让商店
     * 永远出不来列表 / 出两遍。现在两头都堵上 —— 每个来源的回调只认第一次，整体到
     * {@link #LIST_TIMEOUT_MS} 还没齐就按已有的出。迟到的那些照收进下一次列表，不再惊动这一次。
     */
    public static void listAllAvailable(Consumer<List<AppInfo>> callback) {
        List<IAppSource> sources = new ArrayList<>();
        for (IAppSource s : getSources()) {
            if (s.isReady()) sources.add(s);
        }

        if (sources.isEmpty()) {
            callback.accept(List.of());
            return;
        }

        List<AppInfo> merged = new ArrayList<>();
        Set<ResourceLocation> answered = new HashSet<>();
        // 一次性闸：超时那条路与最后一个来源可能撞在一起，两边都得认这一个
        AtomicBoolean done = new AtomicBoolean(false);
        int total = sources.size();

        for (IAppSource s : sources) {
            ResourceLocation id = s.getId();
            try {
                s.listAvailable(list -> {
                    if (done.get()) {
                        // 超时之后才回来的：这一次已经出过列表了，下次进商店会带上它
                        MCphone.LOGGER.warn("[MCphone] 应用来源 {} 回调来晚了，本次列表已经出过", id);
                        return;
                    }
                    if (!answered.add(id)) {
                        // 回调两次的来源：多出来的那次会让同一批 App 在商店里出现两遍
                        MCphone.LOGGER.warn("[MCphone] 应用来源 {} 回调了不止一次，后面的已忽略", id);
                        return;
                    }
                    if (list != null) merged.addAll(list);
                    if (answered.size() == total && done.compareAndSet(false, true)) {
                        callback.accept(List.copyOf(merged));
                    }
                });
            } catch (Throwable t) {
                // 列的时候当场抛的来源：当它答过了，否则这一批永远凑不齐，只能等超时
                MCphone.LOGGER.error("[MCphone] 应用来源 {} 的 listAvailable 抛异常，已跳过", id, t);
                if (answered.add(id) && answered.size() == total && done.compareAndSet(false, true)) {
                    callback.accept(List.copyOf(merged));
                }
            }
        }

        if (done.get()) return;   // 全都是同步来源，已经出过了，不必再挂一个定时器

        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.runAsync(() -> { },
                        CompletableFuture.delayedExecutor(LIST_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                // 回到客户端主线程：callback 里会碰注册表与 GUI 状态（IAppSource 的契约）
                .thenRun(() -> mc.execute(() -> {
                    if (!done.compareAndSet(false, true)) return;
                    List<ResourceLocation> late = new ArrayList<>();
                    for (IAppSource s : sources) {
                        if (!answered.contains(s.getId())) late.add(s.getId());
                    }
                    MCphone.LOGGER.warn("[MCphone] 等了 {} 毫秒还没回来的应用来源: {}，先按已有的出列表",
                            LIST_TIMEOUT_MS, late);
                    callback.accept(List.copyOf(merged));
                }));
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        // 必须走 SpiLoader：一个来源类构造失败会连本地来源一起丢掉，商店直接变空
        int count = 0;
        for (IAppSource s : SpiLoader.loadSafely(IAppSource.class, "应用来源")) {
            try {
                if (register(s)) count++;
            } catch (Throwable t) {
                MCphone.LOGGER.error("[MCphone] 应用来源 {} 登记时抛异常，已跳过",
                        s.getClass().getName(), t);
            }
        }
        MCphone.LOGGER.info("[MCphone] 应用来源扫描完成，共 {} 个", count);
    }
}
