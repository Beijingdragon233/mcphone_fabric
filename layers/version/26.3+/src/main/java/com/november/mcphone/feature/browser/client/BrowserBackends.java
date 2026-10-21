package com.november.mcphone.feature.browser.client;

import com.november.mcphone.MCphone;
import net.minecraft.network.chat.Component;

/**
 * Browser backend registry for 26.3.
 *
 * <p>MCEF has no 26.3 NeoForge artifact, so {@code McefBackend.java} is excluded
 * at mount time. This registry keeps the browser UI and service-provider entry
 * intact while explicitly reporting that no backend is available.</p>
 */
public final class BrowserBackends {

    private BrowserBackends() {}

    public static final String MCEF_MODID = "mcef";

    public static final IBrowserBackend NONE = new IBrowserBackend() {
        @Override public boolean isAvailable() { return false; }

        @Override public Component unavailableReason() {
            return Component.translatable("mcphone.browser.no_backend");
        }

        @Override public IBrowser create(String url, int width, int height) { return null; }
    };

    private static IBrowserBackend current = NONE;

    public static IBrowserBackend get() {
        return current;
    }

    public static boolean set(IBrowserBackend backend) {
        if (backend == null) {
            MCphone.LOGGER.warn("[MCphone] 浏览器后端登记失败: 传入了 null");
            return false;
        }
        if (current != NONE) {
            MCphone.LOGGER.warn("[MCphone] 浏览器后端已由 {} 接管，忽略 {}",
                    current.getClass().getName(), backend.getClass().getName());
            return false;
        }
        current = backend;
        MCphone.LOGGER.info("[MCphone] 浏览器后端已接入: {}", backend.getClass().getName());
        return true;
    }

    public static boolean isMcefLoaded() {
        return false;
    }

    public static void installDefault() {
        MCphone.LOGGER.info("[MCphone] MCEF 尚无 26.3 构建，浏览器功能不可用");
    }
}
