package com.november.mcphone.core.script.client.tex;

import com.november.mcphone.core.script.pkg.AppPackage;
import net.minecraft.resources.ResourceLocation;

/** 包内图片对应的贴图（施工方案 §5.2 image）。 */
public final class AppTextures {

    private AppTextures() {
    }

    /** 包内图片还没有注册成贴图，一律返回 null：image 画占位图。 */
    public static ResourceLocation of(AppPackage pkg, String src) {
        return null;
    }
}
