package com.november.mcphone.feature.browser.client;

import java.awt.event.InputEvent;
import net.minecraft.network.chat.Component;
import net.montoyo.mcef.api.MCEFApi;
import org.cef.browser.CefBrowser;
import org.lwjgl.glfw.GLFW;

/** Adapter for the off-screen browser exposed by MCEF on Forge 40. */
public final class McefBackend implements IBrowserBackend {
    public static final String MOD_ID = "forgecef";

    @Override public boolean isAvailable() {
        var api = MCEFApi.getAPI();
        return api instanceof net.montoyo.mcef.client.ClientProxy proxy
                && !api.isVirtual() && proxy.getCefApp() != null;
    }

    @Override public Component unavailableReason() {
        return Component.translatable("mcphone.browser.mcef_not_ready");
    }

    @Override public IBrowser create(String url, int width, int height) {
        if (!isAvailable()) return null;
        var browser = MCEFApi.getAPI().createBrowser(url, false);
        if (!(browser instanceof CefBrowser cef)) {
            if (browser != null) browser.close();
            return null;
        }
        var handle = new Browser(browser, cef);
        handle.resize(width, height);
        return handle;
    }

    private static final class Browser implements IBrowser {
        private final net.montoyo.mcef.api.IBrowser browser;
        private final CefBrowser cef;
        private boolean closed;
        private int buttons;

        Browser(net.montoyo.mcef.api.IBrowser browser, CefBrowser cef) {
            this.browser = browser;
            this.cef = cef;
        }

        @Override public int textureId() { return browser.getTextureID(); }
        @Override public void resize(int width, int height) {
            browser.resize(Math.max(1, width), Math.max(1, height));
        }
        @Override public void mouseMove(int x, int y) { browser.injectMouseMove(x, y, buttons, false); }
        @Override public void mousePress(int x, int y, int button) {
            if (button < 0 || button > 2) return;
            int mapped = mouseButton(button);
            buttons |= InputEvent.getMaskForButton(mapped);
            browser.injectMouseButton(x, y, buttons, mapped, true, 1);
        }
        @Override public void mouseRelease(int x, int y, int button) {
            if (button < 0 || button > 2) return;
            int mapped = mouseButton(button);
            buttons &= ~InputEvent.getMaskForButton(mapped);
            browser.injectMouseButton(x, y, buttons, mapped, false, 1);
        }
        @Override public void mouseWheel(int x, int y, double amount, int mods) {
            if (amount == 0) return;
            int rotation = (int) Math.copySign(Math.max(1, Math.round(Math.abs(amount))), amount);
            browser.injectMouseWheel(x, y, modifiers(mods), rotation, 0);
        }
        @Override public void keyPress(int key, long scanCode, int mods) {
            browser.injectKeyPressedByKeyCode(key, keyChar(key), modifiers(mods));
        }
        @Override public void keyRelease(int key, long scanCode, int mods) {
            browser.injectKeyReleasedByKeyCode(key, keyChar(key), modifiers(mods));
        }
        @Override public void charTyped(char c, int mods) { browser.injectKeyTyped(c, modifiers(mods)); }
        @Override public void setFocus(boolean focus) {
            if (!focus) buttons = 0;
            cef.setFocus(focus);
        }
        @Override public void loadUrl(String url) { browser.loadURL(url); }
        @Override public String currentUrl() { return java.util.Objects.toString(browser.getURL(), ""); }
        @Override public boolean canGoBack() { return cef.canGoBack(); }
        @Override public void goBack() { cef.goBack(); }
        @Override public boolean canGoForward() { return cef.canGoForward(); }
        @Override public void goForward() { cef.goForward(); }
        @Override public void reload() { cef.reload(); }
        @Override public boolean isLoading() { return browser.isPageLoading(); }
        @Override public void close() {
            if (closed) return;
            closed = true;
            browser.close();
        }
    }

    private static int mouseButton(int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 3 : button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE ? 2 : 1;
    }

    private static int modifiers(int glfw) {
        int result = 0;
        if ((glfw & GLFW.GLFW_MOD_SHIFT) != 0) result |= InputEvent.SHIFT_DOWN_MASK;
        if ((glfw & GLFW.GLFW_MOD_CONTROL) != 0) result |= InputEvent.CTRL_DOWN_MASK;
        if ((glfw & GLFW.GLFW_MOD_ALT) != 0) result |= InputEvent.ALT_DOWN_MASK;
        if ((glfw & GLFW.GLFW_MOD_SUPER) != 0) result |= InputEvent.META_DOWN_MASK;
        return result;
    }

    private static char keyChar(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> '\n';
            case GLFW.GLFW_KEY_TAB -> '\t';
            case GLFW.GLFW_KEY_BACKSPACE -> '\b';
            default -> key >= 32 && key <= 126 ? (char) key : java.awt.event.KeyEvent.CHAR_UNDEFINED;
        };
    }
}
