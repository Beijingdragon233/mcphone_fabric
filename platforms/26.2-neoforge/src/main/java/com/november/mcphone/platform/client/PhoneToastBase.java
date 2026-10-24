package com.november.mcphone.platform.client;

import com.november.mcphone.platform.client.port.PhoneGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

/** 通知生命周期与内容绘制的版本接缝。 */
public abstract class PhoneToastBase implements Toast {
    protected abstract Visibility updateVisibility(ToastManager manager, long time);

    protected abstract void draw(PhoneGraphics g, Font font);

    private Visibility visibility = Visibility.HIDE;

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        visibility = updateVisibility(manager, time);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, Font font, long time) {
        draw(new PhoneGraphics(g), font);
    }
}
