package com.november.mcphone.platform.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** 读取当前界面；26.1.2 仍保留 Minecraft.screen 字段。 */
public final class Screens {
    private Screens() {}

    public static Screen current(Minecraft mc) {
        return mc.screen;
    }
}
