package com.november.mcphone.mixin.client;

import com.november.mcphone.feature.camera.client.CameraHandler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric 的 HUD 元素挂在原版各个绘制方法内部；相机模式又会隐藏原版 HUD，
 * 所以挂在 HOTBAR 后面的取景框会跟着 hotbar 一起被跳过。
 * 在 extractRenderState 的尾部单独画，才能既藏掉原版 HUD，又保住相机界面。
 */
@Mixin(Hud.class)
public abstract class CameraHudMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mcphone$renderCameraOverlay(GuiGraphicsExtractor graphics,
                                              DeltaTracker deltaTracker,
                                              CallbackInfo ci) {
        CameraHandler.onRenderGui(graphics, deltaTracker);
    }
}
