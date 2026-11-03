package com.november.mcphone.platform.client.port;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** 输入控件的事件与提取入口适配。 */
public class PhoneEditBox extends EditBox {
    public PhoneEditBox(Font f, int x, int y, int w, int h, Component message) {
        super(f, x, y, w, h, message);
    }

    public void render(PhoneGraphics g, int x, int y, float partialTick) {
        super.extractRenderState(g.nativeGraphics(), x, y, partialTick);
    }

    public boolean keyPressed(int key, int scan, int mods) {
        return super.keyPressed(new KeyEvent(key, scan, mods));
    }

    public boolean charTyped(char c, int mods) {
        return PhoneInputs.submitCharacter(c, e -> super.charTyped(e));
    }

    public boolean mouseClicked(double x, double y, int b) {
        return super.mouseClicked(PhoneInputs.mouse(x, y, b), PhoneInputs.doubleClick);
    }

    public boolean mouseReleased(double x, double y, int b) {
        return super.mouseReleased(PhoneInputs.mouse(x, y, b));
    }

    public boolean mouseDragged(double x, double y, int b, double dx, double dy) {
        return super.mouseDragged(PhoneInputs.mouse(x, y, b), dx, dy);
    }

    public void tick() {}
}
