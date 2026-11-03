package com.november.mcphone.platform.client;

import com.november.mcphone.platform.client.port.PhoneGraphics;
import com.november.mcphone.platform.client.port.PhonePlayerFaceRenderer;
import net.minecraft.resources.Identifier;

/** 头像绘制入口。 */
public final class PlayerFaces {
    private PlayerFaces() {}

    public static void draw(PhoneGraphics graphics, Identifier texture, int x, int y, int size) {
        PhonePlayerFaceRenderer.draw(graphics, texture, x, y, size);
    }
}
