package com.november.mcphone.platform.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

public final class ImageTextures {
    private ImageTextures() {}

    public static void setPixel(NativeImage image, int x, int y, int color) {
        image.setPixelRGBA(x, y, color);
    }

    public static DynamicTexture dynamic(NativeImage image) {
        return new DynamicTexture(image);
    }
}
