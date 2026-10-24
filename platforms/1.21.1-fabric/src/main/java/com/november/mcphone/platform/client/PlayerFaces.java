package com.november.mcphone.platform.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.resources.ResourceLocation;

public final class PlayerFaces {
    private PlayerFaces() {}

    public static void draw(GuiGraphics graphics, ResourceLocation texture,
                            int x, int y, int size) {
        PlayerFaceRenderer.draw(graphics, texture, x, y, size);
    }
}
