package com.november.mcphone.platform.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** 26.3 的贴图 API：像素写入与动态贴图构造器都已改名/加标签参数。 */
public final class ImageTextures {
    private ImageTextures() {}

    public static void setPixel(NativeImage image, int x, int y, int color) {
        image.setPixel(x, y, color);
    }

    public static DynamicTexture dynamic(NativeImage image) {
        return new DynamicTexture(() -> "mcphone_dynamic_texture", image);
    }
}
