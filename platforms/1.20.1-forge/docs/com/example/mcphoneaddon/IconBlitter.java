package com.example.mcphoneaddon;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Platform copy of the API example's dynamic icon draw. */
final class IconBlitter {

    private IconBlitter() {}

    static void blit(GuiGraphics g, ResourceLocation texture,
                     int x, int y, int size, int frame, int frames) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(texture, x, y, size, size,
                frame * size, 0, size, size, size * frames, size);
        RenderSystem.disableBlend();
    }
}
