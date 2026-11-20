package com.november.mcphone.platform.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/** The phone drawing surface backed by the 1.18.2 render callback's pose stack. */
public final class PhoneGraphics {
    private final PoseStack pose;
    private final Deque<Clip> clips = new ArrayDeque<>();

    public PhoneGraphics(PoseStack pose) {
        this.pose = pose;
    }

    public PoseStack pose() { return pose; }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        GuiComponent.fill(pose, x1, y1, x2, y2, color);
    }

    public void renderOutline(int x, int y, int w, int h, int color) {
        fill(x, y, x+w, y+1, color);
        fill(x, y+h-1, x+w, y+h, color);
        fill(x, y+1, x+1, y+h-1, color);
        fill(x+w-1, y+1, x+w, y+h-1, color);
    }

    public int drawString(Font font, String text, float x, float y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, Component text, float x, float y, int color, boolean shadow) {
        return drawString(font, text.getVisualOrderText(), x, y, color, shadow);
    }

    public int drawString(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        GuiComponent.drawCenteredString(pose, font, text, x, y, color);
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        GuiComponent.drawCenteredString(pose, font, text, x, y, color);
    }

    public void blit(ResourceLocation texture, int x, int y, int w, int h,
                     float u, float v, int srcW, int srcH, int texW, int texH) {
        RenderSystem.setShaderTexture(0, texture);
        GuiComponent.blit(pose, x, y, w, h, u, v, srcW, srcH, texW, texH);
    }

    public void setColor(float r, float g, float b, float a) { RenderSystem.setShaderColor(r, g, b, a); }

    public void flush() { Minecraft.getInstance().renderBuffers().bufferSource().endBatch(); }

    public void renderItem(ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        var mc = Minecraft.getInstance();
        var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
        var buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        try {
            pose.translate(x+8, y+8, 150);
            pose.scale(16, -16, 16);
            mc.getItemRenderer().render(stack, ItemTransforms.TransformType.GUI, false, pose,
                    buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
            buffers.endBatch();
        } finally {
            pose.popPose();
        }
    }

    private record Clip(int left, int top, int right, int bottom) {
        Clip intersect(Clip other) {
            return new Clip(Math.max(left, other.left), Math.max(top, other.top),
                    Math.min(right, other.right), Math.min(bottom, other.bottom));
        }
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        Clip next = new Clip(x1, y1, x2, y2);
        if (!clips.isEmpty()) next = next.intersect(clips.peek());
        clips.push(next);
        applyClip(next);
    }

    public void disableScissor() {
        if (!clips.isEmpty()) clips.pop();
        applyClip(clips.peek());
    }

    private void applyClip(Clip clip) {
        flush();
        if (clip == null) {
            RenderSystem.disableScissor();
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int x = (int) Math.floor(clip.left * scale);
        int y = (int) Math.floor(window.getHeight() - clip.bottom * scale);
        int right = (int) Math.ceil(clip.right * scale);
        int top = (int) Math.ceil(window.getHeight() - clip.top * scale);
        RenderSystem.enableScissor(x, y, Math.max(0, right - x), Math.max(0, top - y));
    }
}
