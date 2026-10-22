package com.november.mcphone.platform.client;

import com.november.mcphone.platform.client.port.PhoneGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** 26.3 的 GUI 绘制接缝。 */
public final class Draw {
    private Draw() {}

    public static boolean poseAtBase(com.mojang.blaze3d.vertex.PoseStack stack) {
        return stack.isEmpty();
    }

    public static void screenBackground(Screen s, PhoneGraphics g, int x, int y, float a) {}

    public static boolean scissorLeaked(PhoneGraphics g) {
        return !g.containsPointInScissor(0, 0);
    }

    public static void browserFrame(PhoneGraphics g, int texture, int x, int y, int w, int h) {
        throw new UnsupportedOperationException(
                "MCEF browser textures require a 26.3 GpuTextureView backend");
    }

    public static void tinted(PhoneGraphics g, Identifier tex,
                              int x, int y, int w, int h, float u, float v,
                              int srcW, int srcH, int texW, int texH, int argb) {
        g.nativeGraphics().blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h,
                srcW, srcH, texW, texH, argb);
    }

    public static void highlighted(PhoneGraphics g, Identifier tex,
                                   int x, int y, int w, int h, float u, float v,
                                   int srcW, int srcH, int texW, int texH, float brightness) {
        int alpha = Math.max(0, Math.min(255, (int) ((brightness - 1.0F) * 255.0F)));
        g.nativeGraphics().blit(RenderPipelines.GUI_NAUSEA_OVERLAY, tex, x, y, u, v, w, h,
                srcW, srcH, texW, texH, ARGB.color(alpha, 255, 255, 255));
    }

    public static boolean canDrawItemIcon(ItemStack stack) {
        var mc = Minecraft.getInstance();
        var state = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(
                state, stack, ItemDisplayContext.GUI, mc.level, mc.player, 0);
        return !state.isEmpty();
    }
}
