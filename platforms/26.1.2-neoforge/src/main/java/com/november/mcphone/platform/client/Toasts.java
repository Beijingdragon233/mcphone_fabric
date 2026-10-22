package com.november.mcphone.platform.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastComponent;

/** 读取通知区管理器；26.1.2 仍由 Minecraft 提供 getter。 */
public final class Toasts {
    private Toasts() {}

    public static ToastComponent manager(Minecraft mc) {
        return mc.getToasts();
    }
}
