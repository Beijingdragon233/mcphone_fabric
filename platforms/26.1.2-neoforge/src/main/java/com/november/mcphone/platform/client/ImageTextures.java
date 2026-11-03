package com.november.mcphone.platform.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.november.mcphone.platform.client.port.PhoneDynamicTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** 26.1 的像素写入与动态贴图构造入口。 */
public final class ImageTextures {
    private ImageTextures() {}

    public static void setPixel(NativeImage image, int x, int y, int color) {
        image.setPixel(x, y, color);
    }

    public static DynamicTexture dynamic(NativeImage image) {
        return new PhoneDynamicTexture(image);
    }
}
