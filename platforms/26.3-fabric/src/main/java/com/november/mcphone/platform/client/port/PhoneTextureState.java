package com.november.mcphone.platform.client.port;

/** 旧版全局贴图调色转成单次绘制的 ARGB。 */
public final class PhoneTextureState {
    private static int color = -1;

    private PhoneTextureState() {}

    private static int channel(float value) {
        return Math.max(0, Math.min(255, (int) (value * 255)));
    }

    public static int color() {
        return color;
    }

    public static void setColor(int argb) {
        color = argb;
    }

    public static void setShaderColor(float r, float g, float b, float a) {
        color = channel(a) << 24 | channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    public static void enableBlend() {}

    public static void defaultBlendFunc() {}

    public static void disableBlend() {}
}
