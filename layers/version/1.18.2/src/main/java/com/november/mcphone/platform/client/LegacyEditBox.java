package com.november.mcphone.platform.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Keeps the small API rename needed by the shared single-line inputs.
 *
 * <p>1.18.2 calls the focus operation {@code setFocus}; newer GUI code uses
 * {@code setFocused}.  Inherited editing, selection, and mouse behavior stay
 * in vanilla's {@link EditBox}.</p>
 */
public class LegacyEditBox extends EditBox {

    public LegacyEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    public void setFocused(boolean focused) {
        super.setFocused(focused);
    }
    public void setY(int y) {
        this.y = y;
    }

    public void render(PhoneGraphics graphics, int mouseX, int mouseY, float partialTick) {
        render(graphics.pose(), mouseX, mouseY, partialTick);
    }
}
