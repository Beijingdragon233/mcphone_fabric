package com.november.mcphone;

import com.mojang.logging.LogUtils;
import com.november.mcphone.core.ModAttachments;
import com.november.mcphone.core.ModCreativeTabs;
import com.november.mcphone.core.ModDataComponents;
import com.november.mcphone.core.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** NeoForge 26.3 mod entry point. */
@Mod(MCphone.MODID)
public final class MCphone {

    /**
     * 共用代码认的是这个名字。探针那一份写的叫 {@code MOD_ID}，
     * 而挂载里 56 个文件写死了 {@code MCphone.MODID} —— 名字也是接口的一部分。
     */
    public static final String MODID = "mcphone";

    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 运行时的真实版本号，tooltip 用它填 %s，语言文件里不写死。
     * 在构造函数里由 modContainer 赋值，不用 ModList 静态查询 ——
     * 那依赖 FML 的类加载时序，取不到时是静默的空值。
     */
    private static String version = "";

    public MCphone(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        com.november.mcphone.core.menu.ModMenus.MENUS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        com.november.mcphone.core.ModSounds.SOUND_EVENTS.register(modEventBus);
        modEventBus.addListener(com.november.mcphone.core.net.NetworkHandler::register);

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.november.mcphone.core.net.RequestThrottle::onPlayerLoggedOut);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.november.mcphone.feature.music.DiscService::onPlayerLoggedOut);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.november.mcphone.feature.chat.ChatImageUploads::onPlayerLoggedOut);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.november.mcphone.feature.terminal.TerminalCharger::onPlayerTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.november.mcphone.feature.chat.ChatImageStore::onServerStarted);

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStartedEvent e) -> {
                    com.november.mcphone.core.script.server.economy.EconomyRuntime.start(e.getServer());
                    com.november.mcphone.core.script.server.ScriptWorkers.start();
                });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> {
                    com.november.mcphone.core.script.server.economy.EconomyRuntime.stop();
                    com.november.mcphone.core.script.server.ScriptWorkers.stop();
                    com.november.mcphone.core.script.net.ScriptRpcHandler.clear();
                });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) ->
                        com.november.mcphone.core.script.server.economy.EconomyRuntime.tick());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                        com.november.mcphone.core.script.server.economy.EconomyCommand.register(e.getDispatcher()));

        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SYNCED,
                com.november.mcphone.core.ServerConfig.SPEC, "mcphone-server.toml");

        modEventBus.addListener(
                com.november.mcphone.feature.terminal.integration.Terminals::onCommonSetup);

        version = modContainer.getModInfo().getVersion().toString();
        LOGGER.info("MCphone 模组加载完成 —— 手机已就绪（NeoForge 26.3，v{}）", version);
    }

    /** 本模组版本号，如 "1.10.2"。模组构造前调用会得到空串。 */
    public static String getVersion() {
        return version;
    }
}
