package com.example.mcphoneaddon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

/** Platform copy of the API example's dynamic icon draw. */
final class IconBlitter {

    private IconBlitter() {}

    static void blit(GuiGraphics g, ResourceLocation texture,
                     int x, int y, int size, int frame, int frames) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
                frame * size, 0, size, size, size, size,
                size * frames, size, -1);
    }
}
