package com.november.mcphone.platform.client.port;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import sun.misc.Unsafe;

/**
 * 验证副手手机这类 HUD 绘制不会把缩放与平移留给后续原版 HUD。
 *
 * <p>26.2 Fabric 的 HUD 元素共用同一个提取器。这里只分配矩阵所需字段，
 * 不初始化窗口、图集或 GPU，走的仍是正式代码使用的 {@link GuiGraphics#withIsolatedPose}。
 */
public final class HudGraphicsPoseTest {
    private static int checks;

    private static GuiGraphicsExtractor graphics() throws Exception {
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var nativeGraphics = (GuiGraphicsExtractor) ((Unsafe) unsafeField.get(null))
                .allocateInstance(GuiGraphicsExtractor.class);
        var poseField = GuiGraphicsExtractor.class.getDeclaredField("pose");
        poseField.setAccessible(true);
        poseField.set(nativeGraphics, new Matrix3x2fStack(16));
        return nativeGraphics;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void drawLikePhone(GuiGraphics graphics) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(420, 280, 0);
            graphics.pose().scale(2, 2, 1);
            // 正式绘制会在 popPose 前把当前矩阵同步给原版提取器。
            graphics.nativeGraphics();
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void balanced(GuiGraphicsExtractor graphics) {
        try {
            graphics.pose().popMatrix();
        } catch (IllegalStateException expected) {
            checks++;
            return;
        }
        throw new AssertionError("HUD 绘制结束后原版矩阵栈多出一层");
    }

    public static void main(String[] args) throws Exception {
        var graphics = graphics();
        var callerPose = new Matrix3x2f().translate(31, 17).scale(1.5f);
        graphics.pose().set(callerPose);

        GuiGraphics.withIsolatedPose(graphics, HudGraphicsPoseTest::drawLikePhone);
        check(graphics.pose().equals(callerPose, 0.001f),
                "手机绘制结束污染了后续 HUD 的平移与缩放");
        balanced(graphics);

        var expected = new IllegalStateException("绘制中断");
        try {
            GuiGraphics.withIsolatedPose(graphics, phone -> {
                drawLikePhone(phone);
                throw expected;
            });
            throw new AssertionError("绘制异常没有传回调用方");
        } catch (IllegalStateException actual) {
            check(actual == expected, "保留原始绘制异常");
        }
        check(graphics.pose().equals(callerPose, 0.001f),
                "异常退出仍恢复后续 HUD 的矩阵");
        balanced(graphics);

        GuiGraphics.withIsolatedPose(graphics, outer -> {
            GuiGraphics.withIsolatedPose(graphics, HudGraphicsPoseTest::drawLikePhone);
            check(graphics.pose().equals(callerPose, 0.001f),
                    "嵌套 HUD 绘制恢复父级矩阵");
            GuiGraphics.withIsolatedPose(graphics, HudGraphicsPoseTest::drawLikePhone);
            check(graphics.pose().equals(callerPose, 0.001f),
                    "连续嵌套 HUD 绘制仍恢复父级矩阵");
        });
        check(graphics.pose().equals(callerPose, 0.001f),
                "嵌套绘制结束不污染后续 HUD");
        balanced(graphics);

        System.out.println("全部通过：" + checks + " 条 HUD 绘制矩阵断言");
    }
}
