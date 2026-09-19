package com.november.mcphone.platform.client;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Vector3f;

/**
 * 2D 绘制栈上的那点变换 —— 全仓唯一碰 {@code g.pose()} 的地方。
 *
 * <p>1.20.1 与 1.21.1 这两档上 {@code GuiGraphics.pose()} 返回 {@code PoseStack}，
 * 里面是一张 {@code Matrix4f}：平移缩放都是【三个数】，而 z 那一维【真的在参与深度测试】。
 * 26.x 换成了 {@code Matrix3x2fStack}，压弹改名、平移缩放只收两个 float、整个栈没有 z。
 * 三支写出来没有一行是一样的，所以按 §九 那条判据收在这一层。
 *
 * <p>这一支的三个数里，{@link #translate} 传的是 {@code 0}（「只平移不抬」），
 * {@link #scale} 传的是 {@code 1}（深度不缩），而 {@link #translateAboveItemModel}
 * 传的 {@code 200} 是【在做事的】：物品模型画在 z≈150，同一深度的字会被它挡住。
 * 那一句在 26.x 上只能改成靠提交顺序，见那一支的类注释。
 *
 * <h2>包名里的 client 是硬要求</h2>
 *
 * dist 隔离那道闸只准路径里带 {@code /client/} 的类引用客户端与图形层的东西。
 */
public final class Transforms {

    private Transforms() {}

    /** 存一次当前变换。与 {@link #pop} 成对用。 */
    public static void push(GuiGraphics g) {
        g.pose().pushPose();
    }

    /** 回到 {@link #push} 之前那一份变换。 */
    public static void pop(GuiGraphics g) {
        g.pose().popPose();
    }

    /** 平移。第三个参数恒为 0：调用方从来没有靠平移改过深度。 */
    public static void translate(GuiGraphics g, double x, double y) {
        g.pose().translate(x, y, 0.0D);
    }

    /** 缩放。第三个参数恒为 1：深度方向从来不缩。 */
    public static void scale(GuiGraphics g, double sx, double sy) {
        g.pose().scale((float) sx, (float) sy, 1.0F);
    }

    /** 平移到物品模型之上那一层。见类注释里那个 200。 */
    public static void translateAboveItemModel(GuiGraphics g, double x, double y) {
        g.pose().translate(x, y, 200.0D);
    }

    /**
     * 把逻辑坐标按【当前那张矩阵】投到屏幕上取 x。
     *
     * <p>走的是原来那句 {@code pose().last().pose()} —— {@code last()} 取栈顶那一份，
     * 而 {@code pose()} 把它摊成一张 {@code Matrix4f}。26.x 上那个栈本身就是矩阵、
     * 也没有 {@code last()} 这一层，所以这一句在两支上不是「换个名字」的关系。
     */
    public static float mapX(GuiGraphics g, float x, float y) {
        return g.pose().last().pose().transformPosition(x, y, 0.0F, new Vector3f()).x;
    }

    /** 同上，取 y。与 {@link #mapX} 各算一次，省得把 {@code Vector3f} 递来递去。 */
    public static float mapY(GuiGraphics g, float x, float y) {
        return g.pose().last().pose().transformPosition(x, y, 0.0F, new Vector3f()).y;
    }
}
