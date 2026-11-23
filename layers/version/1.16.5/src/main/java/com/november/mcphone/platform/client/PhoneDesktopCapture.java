package com.november.mcphone.platform.client;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.platform.NativeImage;

public final class PhoneDesktopCapture {
    private PhoneDesktopCapture() {}

    public static void capture(Consumer<NativeImage> receiver) {
        Minecraft mc = Minecraft.getInstance();
        receiver.accept(Screenshot.takeScreenshot(mc.getWindow().getWidth(), mc.getWindow().getHeight(),
                mc.getMainRenderTarget()));
    }
    public static int readAbgr(NativeImage image, int x, int y) {
        return image.getPixelRGBA(x, y);
    }
}
