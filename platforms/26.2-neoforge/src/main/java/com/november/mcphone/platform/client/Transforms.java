package com.november.mcphone.platform.client;

import com.november.mcphone.platform.client.port.PhoneGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Vector2f;

/** 2D 绘制栈上的变换。 */
public final class Transforms {
    private Transforms() {}

    public static void push(GuiGraphicsExtractor g) {
        g.pose().pushMatrix();
    }

    public static void push(PhoneGraphics g) {
        g.pose().pushPose();
    }

    public static void pop(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    public static void pop(PhoneGraphics g) {
        g.pose().popPose();
    }

    public static void translate(GuiGraphicsExtractor g, double x, double y) {
        g.pose().translate((float) x, (float) y);
    }

    public static void translate(PhoneGraphics g, double x, double y) {
        g.pose().translate((float) x, (float) y, 0);
    }

    public static void scale(GuiGraphicsExtractor g, double sx, double sy) {
        g.pose().scale((float) sx, (float) sy);
    }

    public static void scale(PhoneGraphics g, double sx, double sy) {
        g.pose().scale((float) sx, (float) sy, 1);
    }

    public static void translateAboveItemModel(GuiGraphicsExtractor g, double x, double y) {
        g.pose().translate((float) x, (float) y);
    }

    public static void translateAboveItemModel(PhoneGraphics g, double x, double y) {
        g.pose().translate((float) x, (float) y, 0);
    }

    public static float mapX(GuiGraphicsExtractor g, float x, float y) {
        return g.pose().transformPosition(x, y, new Vector2f()).x;
    }

    public static float mapX(PhoneGraphics g, float x, float y) {
        return g.pose().last().pose().transformPosition(x, y, 0, new org.joml.Vector3f()).x;
    }

    public static float mapY(GuiGraphicsExtractor g, float x, float y) {
        return g.pose().transformPosition(x, y, new Vector2f()).y;
    }

    public static float mapY(PhoneGraphics g, float x, float y) {
        return g.pose().last().pose().transformPosition(x, y, 0, new org.joml.Vector3f()).y;
    }

    public static void scissor(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.pose().pushMatrix();
        g.pose().identity();
        g.enableScissor(x0, y0, x1, y1);
        g.pose().popMatrix();
    }

    public static void scissor(PhoneGraphics g, int x0, int y0, int x1, int y1) {
        g.enableScissor(x0, y0, x1, y1);
    }
}
