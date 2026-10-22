package com.november.mcphone.platform.client;

import com.november.mcphone.platform.client.port.PhoneGraphics;
import com.november.mcphone.platform.client.port.PhoneInputs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/** 输入控件跨版本动作。 */
public final class EditBoxes {
    private EditBoxes() {}

    public static void moveCursorToEnd(EditBox box) {
        box.moveCursorToEnd(false);
    }

    public static boolean click(AbstractWidget w, double mx, double my, int pageButton) {
        return w.mouseClicked(PhoneInputs.mouse(mx, my, pageButton), false);
    }

    public static boolean drag(AbstractWidget w, double mx, double my, int pageButton,
                               double dx, double dy) {
        return w.mouseDragged(PhoneInputs.mouse(mx, my, pageButton), dx, dy);
    }

    public static boolean key(AbstractWidget w, int keyCode, int scanCode, int modifiers) {
        return w.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
    }

    public static boolean character(AbstractWidget w, char c, int modifiers) {
        return w.charTyped(new CharacterEvent(c));
    }

    public static void render(AbstractWidget w, PhoneGraphics g, int mouseX, int mouseY,
                              float partialTick) {
        w.extractRenderState(g.nativeGraphics(), mouseX, mouseY, partialTick);
    }

    public static void render(AbstractWidget w, GuiGraphicsExtractor g, int mouseX, int mouseY,
                              float partialTick) {
        w.extractRenderState(g, mouseX, mouseY, partialTick);
    }
}
