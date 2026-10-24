package com.november.mcphone.platform.client.port;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** 新版动态纹理需要调试标签。 */
public class PhoneDynamicTexture extends DynamicTexture {
    public PhoneDynamicTexture(NativeImage image) {
        super(() -> "MCphone 图片", image);
    }
}
