package com.november.mcphone.platform.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

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
        box.moveCursorToEnd(false);
    }

    /**
     * 把一次点击转给页面里嵌的原版控件。
     *
     * <p>这一支上原版控件收的是 {@code MouseButtonEvent}，所以要就地造出事件对象：
     * {@code pageButton} 是本模组编号（左 = 0），必须先经 {@code nativeButton} 换成
     * 原生编号（左 = 1），因为 {@code AbstractWidget.isValidClickButton} 判的是
     * {@code buttonInfo().button() == 1}。
     *
     * <p>{@code doubleClick} 只能传 {@code false}：本模组的对外页面接口
     * （{@code IPhonePage.mouseClicked}）本来就不带这个信息，老三支同样传不进去，
     * 所以「双击选中一个词」这一条在四支上都没有 —— 这里不比老三支少什么。
     * {@code modifiers} 传 0 也是有据的：{@code AbstractWidget} 与
     * {@code AbstractTextAreaWidget} 里没有任何一处读 {@code buttonInfo().modifiers()}。
     */
    public static boolean click(AbstractWidget w, double mx, double my, int pageButton) {
        return w.mouseClicked(new MouseButtonEvent(mx, my,
                new MouseButtonInfo(PhoneScreenBase.nativeButton(pageButton), 0)), false);
    }

    /** 同上，拖动。 */
    public static boolean drag(AbstractWidget w, double mx, double my, int pageButton,
                               double dx, double dy) {
        return w.mouseDragged(new MouseButtonEvent(mx, my,
                new MouseButtonInfo(PhoneScreenBase.nativeButton(pageButton), 0)), dx, dy);
    }

    /**
     * 把一次按键转给页面里嵌的原版控件。
     *
     * <p>26.3 的 {@code KeyEvent(int key, int keycode, int modifiers)} 里第一位是
     * <b>scancode</b>（见 {@code SDLEventHandler.handleKeyEvent}），而调用方那三个值是从
     * 屏幕的按键派发里来的同一套编号，所以按位置原样传就是对的。
     */
    public static boolean key(AbstractWidget w, int keyCode, int scanCode, int modifiers) {
        return w.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
    }

    /** 把一个字符转给页面里嵌的原版控件。26.3 上 {@code CharacterEvent} 只收码点。 */
    public static boolean character(AbstractWidget w, char c, int modifiers) {
        return w.charTyped(new CharacterEvent(c));
    }

}
