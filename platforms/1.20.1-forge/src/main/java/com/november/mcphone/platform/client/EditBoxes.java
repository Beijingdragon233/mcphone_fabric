package com.november.mcphone.platform.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;

/**
 * 单行输入框上的一些跨版本对不上的动作。
 *
 * <h2>为什么要有这个门面</h2>
 *
 * {@code EditBox.moveCursorToEnd} 在 1.21 上收一个 {@code boolean select}
 * （要不要顺手把从原光标位到末尾那段选中），1.20.1 上<b>无参</b>。
 * 两支<b>没有</b>互相兼容的重载 —— 照哪一支写，另一支都编不过。
 *
 * <h2>为什么门面不带那个参数</h2>
 *
 * 全仓两处调用点用的都是「不选中」（1.21 侧写的是 {@code false}），
 * 而无参那一支的语义正是不选中。真需要「移到末尾并选中」时再加一个方法，
 * 别给这个方法补一个 1.20.1 上无法实现的参数。
 */
public final class EditBoxes {

    private EditBoxes() {}

    /** 光标移到末尾，不选中。 */
    public static void moveCursorToEnd(EditBox box) {
        box.moveCursorToEnd();
    }

    /**
     * 把一次点击转给页面里嵌的原版控件。{@code pageButton} 是本模组编号（左 = 0，
     * 见 {@code api/client/ui/IPhonePage}），这一支上它与原生编号是同一套，直接用。
     */
    public static boolean click(AbstractWidget w, double mx, double my, int pageButton) {
        return w.mouseClicked(mx, my, pageButton);
    }

    /** 同上，拖动。 */
    public static boolean drag(AbstractWidget w, double mx, double my, int pageButton,
                               double dx, double dy) {
        return w.mouseDragged(mx, my, pageButton, dx, dy);
    }

    /** 把一次按键转给页面里嵌的原版控件。三个参数都是原生值，原样传。 */
    public static boolean key(AbstractWidget w, int keyCode, int scanCode, int modifiers) {
        return w.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 把一个字符转给页面里嵌的原版控件。 */
    public static boolean character(AbstractWidget w, char c, int modifiers) {
        return w.charTyped(c, modifiers);
    }

}
