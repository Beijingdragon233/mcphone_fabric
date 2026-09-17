package com.november.mcphone.core.script.client;

import com.november.mcphone.MCphone;
import com.november.mcphone.api.client.app.IPhoneApp;
import com.november.mcphone.api.client.store.AppInfo;
import com.november.mcphone.api.client.store.IAppSource;
import com.november.mcphone.api.sdk.SdkGate;
import com.november.mcphone.core.client.PhoneScreenRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 本地脚本来源（施工方案 §14.1）：玩家放进 {@code mcphone/apps/} 的 {@code .vue} 与 zip。
 *
 * <p>两个方法都同步回调 —— 读的是本机磁盘，没有等的必要，而回调契约只要求在客户端主线程。
 *
 * <p><b>只列不在目录里的</b>：脚本 App 装过一次就进了 {@code CATALOG}，此后卸载再装走的是既有的
 * {@code LocalAppSource}（它列的正是"目录里未安装的"）。这里不再列一遍，否则商店里会出现两条一模一样的。
 *
 * <p><b>只认 {@code deploy: "client"}</b>（§14.2）：P0 的清单里根本没有 {@code deploy} 与
 * {@code capabilities} 字段，也没有后端通道 —— 包里就算带了 {@code server.js} 也只是一个不会被执行的文件。
 * 判据因此天然成立，不需要额外的检查。
 */
public final class LocalScriptSource implements IAppSource {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "local_script");

    /** id → 适配器。同一个包只有一个实例：注册表里那个与商店里那个必须是同一个。 */
    private static final Map<ResourceLocation, ScriptAppAdapter> ADAPTERS = new HashMap<>();

    /** 这一局登记过（或试过）的 id。进世界每次都会扫一遍目录，这张表挡住重复登记的告警。 */
    private static final Set<ResourceLocation> TRIED = new HashSet<>();

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("mcphone.store.source.local_script");
    }

    /**
     * 同步回调，<b>只在客户端主线程调</b>。契约允许来源在后台线程干活（{@link IAppSource}），
     * 这一份不行：{@link AppInfo#of} 会问 {@code getIconTexture()}，那条路会把图标传进显存。
     */
    @Override
    public void listAvailable(Consumer<List<AppInfo>> callback) {
        List<AppInfo> out = new ArrayList<>();
        for (ScriptApp app : ScriptAppFolder.scan()) {
            // 先过一遍 adapter()：玩家把文件换成新版本之后，那一步把注册表里的旧实例换掉。
            // 放在下面那个 continue 之后就永远走不到 —— 换过的包恰好是"已经在目录里"的那些
            ScriptAppAdapter adapter = adapter(app);
            if (PhoneScreenRegistry.getApp(app.id()) != null) continue;   // 已经在目录里，交给 LocalAppSource
            out.add(AppInfo.of(adapter, ID, blockedReason(app)));
        }
        callback.accept(out);
    }

    @Override
    public void install(AppInfo info, Consumer<IPhoneApp> onSuccess, Consumer<Component> onError) {
        ScriptAppAdapter adapter = null;
        for (ScriptApp app : ScriptAppFolder.scan()) {
            if (app.id().equals(info.id())) {
                adapter = adapter(app);
                break;
            }
        }
        if (adapter == null) {
            // 玩家在商店开着的时候把文件删了
            onError.accept(Component.translatable("mcphone.store.error.not_found", info.id().toString()));
            return;
        }
        Component blocked = blockedReason(adapter.script());
        if (blocked != null) {
            // 界面已经把按钮画灰了，这一道是给"不走界面的调用方"的：门控写在两处，
            // 少了哪一处都能让一个用不了的 App 装进主屏
            onError.accept(blocked);
            return;
        }
        if (!PhoneScreenRegistry.install(adapter)) {
            onError.accept(Component.translatable("mcphone.store.error.install_failed", info.id().toString()));
            return;
        }
        onSuccess.accept(adapter);
    }

    /**
     * 这个包的 {@code sdk} 段本机满不满足（§23.4）。满足返回 null。
     *
     * <p>本机版本低于声明 → 标「需要更新 MCphone」并不可安装；高于声明 → 正常，契约只增不减。
     *
     * <p><b>这是 UX，不是边界</b>（§13.8）：这一段整个删掉也只是让商店的按钮不灰，
     * 真正的判定在服务端审批部署那一侧，调的是同一个 {@link SdkGate}。
     */
    private static Component blockedReason(ScriptApp app) {
        Map<String, Integer> missing = SdkGate.unsatisfied(app.manifest().sdk());
        if (missing.isEmpty()) return null;
        MCphone.LOGGER.info("[MCphone] 脚本 App {} 要的 SDK 本机给不了: {}（本机 {}）",
                app.id(), app.manifest().sdk(), missing);
        return Component.translatable("mcphone.store.needs_update");
    }

    /**
     * 启动恢复（§3.2）：把<b>存档里记着装过的</b>那几个脚本 App 登记进目录，不改安装状态。
     *
     * <p>必须早于读存档状态：{@code loadState()} 只把"目录里存在的 id"装回已安装集合，
     * 末尾又按当前集合覆写存档。不先登记的话，重启之后已安装的脚本 App 会从主屏消失，
     * 而且那一次覆写会把它从存档里也抹掉 —— 玩家再也装不回原来的位置。
     *
     * <p><b>只恢复装过的，不是把目录里的全登记一遍</b>：全登记的话它们就都成了"目录里未安装的"，
     * 于是改由既有的 LocalAppSource 列出来 —— 玩家会看到同一个没装的 App 重连一次就从
     * 「本机脚本」跳到「本机」那一组，而这个来源的 install 从此再也走不到。
     *
     * @return 这一次新登记了几个
     */
    public static int registerAll() {
        // 先让内建那批进目录：register 不会自己触发 SPI 扫描，而"先注册者胜"。
        // 脚本先进去的话，一个 .vue 声明 someaddon:foo 就能把那个附属模组的 App 挡在门外
        PhoneScreenRegistry.getAppCount();

        Set<ResourceLocation> installed = PhoneScreenRegistry.savedInstalledIds();
        int n = 0;
        for (ScriptApp app : ScriptAppFolder.scan()) {
            // 换过的包在这一步被换进注册表，所以每次进世界都跟得上磁盘上的版本
            ScriptAppAdapter adapter = adapter(app);
            if (!installed.contains(app.id())) continue;
            if (!TRIED.add(app.id())) continue;   // 这一局试过了，别让每次进世界都重报一次 id 冲突
            if (PhoneScreenRegistry.register(adapter)) n++;
        }
        return n;
    }

    /**
     * 同一个 id 只造一个适配器；玩家把文件换成新版本之后（重新编过），换上新的那个。
     *
     * <p>换的时候是原子的：先把旧实例从目录里换下来，成了才撤它的贴图。反过来的话，
     * 换失败时那个 App 会留在目录里而贴图已经没了 —— 主屏上一个点开是白图的 App。
     */
    private static ScriptAppAdapter adapter(ScriptApp app) {
        ScriptAppAdapter known = ADAPTERS.get(app.id());
        if (known != null && known.script() == app) return known;

        ScriptAppAdapter fresh = new ScriptAppAdapter(app);
        ADAPTERS.put(app.id(), fresh);
        if (known == null) return fresh;

        if (PhoneScreenRegistry.replace(known, fresh)) {
            known.onUninstall();   // 旧那份的图标与包内贴图，这时候才还
            MCphone.LOGGER.info("[MCphone] 脚本 App {} 换成了 {}（{}）",
                    app.id(), app.manifest().version(), app.file());
        } else if (PhoneScreenRegistry.getApp(app.id()) != null) {
            // 目录里那个 id 是别人的（第一次登记时就撞车了）。按设计不抢，但得说一声，
            // 否则玩家改完文件看不到任何变化，也不知道为什么
            MCphone.LOGGER.warn("[MCphone] {} 改过了，但目录里的 '{}' 是别人登记的，这个包不生效",
                    app.file(), app.id());
        }
        return fresh;
    }
}
