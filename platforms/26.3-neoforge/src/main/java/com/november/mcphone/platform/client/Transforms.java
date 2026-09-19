package com.november.mcphone.platform.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Vector2f;

/**
 * 2D 绘制栈上的那点变换 —— 全仓唯一碰 {@code g.pose()} 的地方。
 *
 * <h2>这一支与另两支差在哪</h2>
 *
 * 1.20.1 / 1.21.1 上 {@code GuiGraphics.pose()} 返回 {@code PoseStack}，里面是一张
 * {@code Matrix4f}，所以平移与缩放都是【三个数】，而 z 那一维是真在参与深度测试的。
 * 26.x 把界面那套换成 2D 栈：{@code GuiGraphicsExtractor.pose()} 返回
 * {@code org.joml.Matrix3x2fStack}，压弹叫 {@code pushMatrix()} / {@code popMatrix()}，
 * 平移缩放【只收两个 float】（{@code javap} 对着 joml-1.10.9 验在），
 * 【整个栈没有 z】。
 *
 * <p>所以这不是换个名字的事：{@code translate(x, y, 0)} 的第三个参数在这里没有去处。
 * 传 0 的那些地方本来就是「只平移不抬」，丢掉那个 0 语义一模一样；
 * 唯一不是 0 的是 {@link #translateAboveItemModel}，那个 200 真的在做事，单独说。
 *
 * <h2>{@code translateAboveItemModel} 那个 z 怎么办</h2>
 *
 * 另两支写的是 {@code translate(x, y, 200)}，注释说得很清楚：物品模型画在 z≈150，
 * 同一深度的字会被它挡住，所以要把字抬到 200 那一层。26.x 的 2D 栈【没有这一维】，
 * 挡不挡这件事只能落在【提交顺序】上 —— 而调用方（脚本渲染器 {@code drawItemCount}
 * 的调用点）本来就是先画物品、紧接着画数量，按顺序字就在物品上面。
 *
 * <p>所以这一支收下来就是普通平移。【这条推断还没在游戏里验过】：数量角标到底会不会
 * 被物品模型压住，得开起来看一眼，别把它当成已确认的事实。
 *
 * <h2>包名里的 client 是硬要求</h2>
 *
 * dist 隔离那道闸只准路径里带 {@code /client/} 的类引用客户端与图形层的东西。
 */
public final class Transforms {

    private Transforms() {}

    /** 存一次当前变换。与 {@link #pop} 成对用。 */
    public static void push(GuiGraphicsExtractor g) {
        g.pose().pushMatrix();
    }

    /** 回到 {@link #push} 之前那一份变换。 */
    public static void pop(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    /**
     * 平移。另两支在这一句上传的第三个参数是 {@code 0} —— 那是「不抬深度」，
     * 这一支没有深度那一维，也就不需要写它。
     *
     * <p>参数收 {@code double} 是为了让调用点一个字不用改：那边
     * {@code PoseStack.translate} 吃的是三个 double，这一支转成 float 交给
     * {@code Matrix3x2f}（它【只有】float 版，javap 验在）。
     */
    public static void translate(GuiGraphicsExtractor g, double x, double y) {
        g.pose().translate((float) x, (float) y);
    }

    /** 缩放。另两支传的是 {@code (sx, sy, 1)}，那个 1 同理没有对应物。 */
    public static void scale(GuiGraphicsExtractor g, double sx, double sy) {
        g.pose().scale((float) sx, (float) sy);
    }

    /**
     * 平移到「应当画在物品模型之上」的位置。另两支靠 {@code z = 200} 做到这件事，
     * 这一支靠提交顺序 —— 理由与【尚未在游戏里复核】那句都在类注释里，这里不重抄。
     */
    public static void translateAboveItemModel(GuiGraphicsExtractor g, double x, double y) {
        g.pose().translate((float) x, (float) y);
    }

    /**
     * 把逻辑坐标按【当前变换】投到屏幕上取 x。另两支走的是
     * {@code pose().last().pose()} 那张 {@code Matrix4f} 再
     * {@code transformPosition(x, y, 0, Vector3f)}；这一支的栈【本身】就是当前那张
     * {@code Matrix3x2f}（{@code Matrix3x2fStack} 继承它），少的那个参数不是丢了的
     * 语义 —— 这一层没有深度。
     *
     * <p>取整方向那件事留在调用方（{@code GuiUtil.enableScissor}），那是业务；
     * 这里只管「这个点变换之后落在哪」。
     */
    public static float mapX(GuiGraphicsExtractor g, float x, float y) {
        return g.pose().transformPosition(x, y, new Vector2f()).x;
    }

    /** 同上，取 y。与 {@link #mapX} 各算一次，省得把 {@code Vector2f} 递来递去。 */
    public static float mapY(GuiGraphicsExtractor g, float x, float y) {
        return g.pose().transformPosition(x, y, new Vector2f()).y;
    }
}
