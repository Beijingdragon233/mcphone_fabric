package com.november.mcphone.api.client.ui;

/** 可保存的内容区布局；坐标与 PhoneCanvas 一致，scale 是手机到窗口 GUI 坐标的倍率。 */
public final class PhoneViewport {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final double scale;

    public PhoneViewport(int x, int y, int width, int height, double scale) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scale = scale;
    }

    public int x() { return x; }

    public int y() { return y; }

    public int width() { return width; }

    public int height() { return height; }

    public double scale() { return scale; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PhoneViewport)) return false;
        PhoneViewport that = (PhoneViewport) other;
        return this.x == that.x
                && this.y == that.y
                && this.width == that.width
                && this.height == that.height
                && Double.compare(this.scale, that.scale) == 0;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(x);
        hash = 31 * hash + java.util.Objects.hashCode(y);
        hash = 31 * hash + java.util.Objects.hashCode(width);
        hash = 31 * hash + java.util.Objects.hashCode(height);
        hash = 31 * hash + java.util.Objects.hashCode(scale);
        return hash;
    }

    @Override public String toString() { return "PhoneViewport[x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + ", scale=" + scale + "]"; }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
