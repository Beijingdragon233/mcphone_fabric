package com.november.mcphone.platform.client;

import com.november.mcphone.platform.client.port.PhoneGraphics;
import com.november.mcphone.platform.client.port.PhoneInputs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 手机页面在 26.3 上的输入与渲染入口。 */
public abstract class PhoneScreenBase extends Screen {
    protected boolean shouldDrawBackground() {
        return true;
    }

    protected PhoneScreenBase(Component title) {
        super(title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float a) {
        // 手机页面绘制时会把缩放矩阵写回原版提取器；界面退出时必须恢复，
        // 否则同一帧后续的 F3 调试层会沿用手机的缩放与平移。
        PhoneGraphics.withIsolatedPose(g, graphics -> render(graphics, x, y, a));
    }

    public void render(PhoneGraphics g, int x, int y, float a) {
        super.extractRenderState(g.nativeGraphics(), x, y, a);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int x, int y, float a) {
        if (shouldDrawBackground()) {
            PhoneGraphics.withIsolatedPose(g, graphics -> renderBackground(graphics, x, y, a));
        }
    }

    public void renderBackground(PhoneGraphics g, int x, int y, float a) {
        super.extractBackground(g.nativeGraphics(), x, y, a);
    }

    @Override
    public void resize(int w, int h) {
        resize(Minecraft.getInstance(), w, h);
    }

    public void resize(Minecraft mc, int w, int h) {
        super.resize(w, h);
    }

    public void init(Minecraft mc, int w, int h) {
        super.init(w, h);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        return keyPressed(e.key(), e.keycode(), e.modifiers());
    }

    public boolean keyPressed(int key, int scan, int mods) {
        return super.keyPressed(new KeyEvent(key, scan, mods));
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        return keyReleased(e.key(), e.keycode(), e.modifiers());
    }

    public boolean keyReleased(int key, int scan, int mods) {
        return super.keyReleased(new KeyEvent(key, scan, mods));
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        var previous = PhoneInputs.character;
        PhoneInputs.character = e;
        try {
            return charTyped(e.codepoint(), PhoneInputs.modifiers());
        } finally {
            PhoneInputs.character = previous;
        }
    }

    public boolean charTyped(int codePoint, int mods) {
        return PhoneInputs.dispatchCharacter(codePoint, unit -> charTyped((char) unit, mods));
    }

    public boolean charTyped(char c, int mods) {
        return PhoneInputs.submitCharacter(c, e -> super.charTyped(e));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        var previous = PhoneInputs.mouse;
        boolean previousTwice = PhoneInputs.doubleClick;
        PhoneInputs.mouse = e;
        PhoneInputs.doubleClick = twice;
        try {
            // 26.3 原生左键是 1；共享页面沿用旧接口的 0 基按钮值。
            return mouseClicked(e.x(), e.y(), pageButton(e.button()));
        } finally {
            PhoneInputs.mouse = previous;
            PhoneInputs.doubleClick = previousTwice;
        }
    }

    public boolean mouseClicked(double x, double y, int b) {
        return super.mouseClicked(PhoneInputs.mouse(x, y, b), PhoneInputs.doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        return mouseReleased(e.x(), e.y(), pageButton(e.button()));
    }

    public boolean mouseReleased(double x, double y, int b) {
        return super.mouseReleased(PhoneInputs.mouse(x, y, b));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return mouseDragged(e.x(), e.y(), pageButton(e.button()), dx, dy);
    }

    public boolean mouseDragged(double x, double y, int b, double dx, double dy) {
        return super.mouseDragged(PhoneInputs.mouse(x, y, b), dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        return onScroll(x, y, sx, sy) || super.mouseScrolled(x, y, sx, sy);
    }

    protected boolean onScroll(double x, double y, double sx, double sy) {
        return false;
    }

    public static boolean hasControlDown() {
        return KeyModifiers.ctrl();
    }

    public static boolean hasShiftDown() {
        return KeyModifiers.shift();
    }

    public static boolean hasAltDown() {
        return KeyModifiers.alt();
    }

    public static boolean isCopy(int key) {
        return PhoneInputs.isCopy(key);
    }

    public static int pageButton(int nativeButton) {
        return nativeButton >= 1 && nativeButton <= 5 ? nativeButton - 1 : nativeButton;
    }

    public static int nativeButton(int pageButton) {
        return pageButton >= 0 && pageButton <= 4 ? pageButton + 1 : pageButton;
    }
}
