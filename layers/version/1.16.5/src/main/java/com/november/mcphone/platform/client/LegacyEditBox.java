package com.november.mcphone.platform.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.text.ITextComponent;

/**
 * Keeps the small API rename needed by the shared single-line inputs.
 *
 * <p>1.16.5 calls the focus operation {@code setFocus}; newer GUI code uses
 * {@code setFocused}.  Inherited editing, selection, and mouse behavior stay
 * in vanilla's {@link TextFieldWidget}.</p>
 */
public class LegacyEditBox extends TextFieldWidget {

    public LegacyEditBox(FontRenderer font, int x, int y, int width, int height, ITextComponent message) {
        super(font, x, y, width, height, message);
    }

    public void setFocused(boolean focused) {
        super.setFocus(focused);
    }
    public void setY(int y) {
        this.y = y;
    }

    public void render(PhoneGraphics graphics, int mouseX, int mouseY, float partialTick) {
        render(graphics.pose(), mouseX, mouseY, partialTick);
    }
}
