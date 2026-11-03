package com.november.mcphone.platform.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.resources.Identifier;
import com.november.mcphone.platform.client.port.PhoneGraphics;

/** 26.3 的头像绘制入口，包含头部和帽子层。 */
public final class PlayerFaces {
    private PlayerFaces() {}

    public static void draw(GuiGraphicsExtractor graphics, Identifier texture,
                            int x, int y, int size) {
        PlayerFaceExtractor.extractRenderState(graphics, texture, x, y, size, true, false, -1);
    }

    public static void draw(PhoneGraphics graphics, Identifier texture,
                            int x, int y, int size) {
        draw(graphics.nativeGraphics(), texture, x, y, size);
    }
}
