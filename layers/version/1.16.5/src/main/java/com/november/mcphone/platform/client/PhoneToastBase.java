package com.november.mcphone.platform.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;

/** 版本通知生命周期接缝；内容与合并计时由共用 PhoneToast 定义。 */
public abstract class PhoneToastBase implements Toast {
    protected abstract Visibility updateVisibility(ToastComponent manager,long time);
    protected abstract void draw(PhoneGraphics g,Font font);
    @Override public Visibility render(com.mojang.blaze3d.vertex.PoseStack pose,ToastComponent manager,long time) {
        draw(new PhoneGraphics(pose),manager.getMinecraft().font); return updateVisibility(manager,time);
    }
}
