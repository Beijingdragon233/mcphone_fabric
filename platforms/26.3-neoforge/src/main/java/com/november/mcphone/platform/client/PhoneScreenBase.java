package com.november.mcphone.platform.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 手机这一族界面的基类 —— 把 26.x 换掉的【输入事件与渲染入口】挡在共用代码之外。
 *
 * <h2>为什么必须有这一层</h2>
 *
 * 26.x 把一整个派发族换成了记录对象：{@code mouseClicked(double,double,int)} 变成
 * {@code mouseClicked(MouseButtonEvent,boolean)}，{@code keyPressed(int,int,int)} 变成
 * {@code keyPressed(KeyEvent)}，{@code charTyped(char,int)} 变成
 * {@code charTyped(CharacterEvent)}，{@code render(GuiGraphics,...)} 变成
 * {@code extractRenderState(GuiGraphicsExtractor,...)}，{@code resize(Minecraft,int,int)}
 * 变成 {@code resize(int,int)}。
 *
 * <b>这些是覆写，不是调用</b>：照另一支的形状写不会编译报错，只会【永远不被调到】——
 * 键鼠在那一支上安静地失灵，没有任何东西会说话。而方法长什么样，门面改不了：
 * 门面能改「怎么调一个方法」，改不了「一个方法被谁覆写」。所以只能有一个
 * 每支一份的基类替子类覆写掉原版那几个，再转调一组两支同形的中立方法。
 * 子类于是只实现中立那一半，可以整个进共用层。
 *
 * <h2>中立方法的名字为什么照 1.21.1 的形状起</h2>
 *
 * 因为 {@code shared/} 里那 32 处覆写与 25 处 {@code super.} 转调已经写熟了那个形状。
 * 这一层的活是【把差异吃在这里】，不是顺手统一命名。名字换一套的话，改动会散到
 * 共用层里去，那正是要避免的方向。
 *
 * <h2>为什么默认实现要拿"这一次派发"的事件去调原版，而不是直接 return false</h2>
 *
 * 子类里那些 {@code super.mouseClicked(...)} 是要真的把事件交回原版走一遍的 ——
 * 原版靠那一下做控件命中测试与焦点。拿 {@code return false} 搪塞，症状不是崩溃，
 * 是按钮点不着、焦点切不动，而且只在被子类覆写过的那几个界面上。
 *
 * <p>所以每次覆写进来先把事件记在字段里，中立方法的默认实现拿它去调
 * {@code super.<26.x 形状>}，出来的顺序与 1.21.1 一致：先子类逻辑、再原版派发。
 * 记字段这件事看着脏，但派发全程在渲染线程上、且不会重入（原版一次点击只进一次
 * {@code mouseClicked}），{@code finally} 里立刻擦回去。
 */
public abstract class PhoneScreenBase extends Screen {

    protected PhoneScreenBase(Component title) {
        super(title);
    }

    // ==== 这一次派发带进来的原版事件，供中立方法的默认实现交回原版 ====

    private MouseButtonEvent pendingButton;
    private boolean pendingDoubleClick;
    private KeyEvent pendingKey;
    private CharacterEvent pendingChar;

    // ==== 滚轮：26.x 的签名与 1.21.1 相同，但子类认的是 onScroll 这个名字 ====

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return onScroll(mouseX, mouseY, scrollX, scrollY)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * 滚轮。返回 true 表示这一下被这个界面吃掉了，false 交回原版。
     *
     * <p>名字与参数照 1.21.1 那份基类，一字不改 —— 共用代码里三个界面覆写的
     * 是这个名字，而这一层存在的意义就是让子类不必知道外面叫什么。
     */
    protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    // ==== 鼠标：26.x 收 MouseButtonEvent，共用代码认 (x, y, button) ====

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        pendingButton = event;
        pendingDoubleClick = doubleClick;
        try {
            return mouseClicked(event.x(), event.y(), event.button());
        } finally {
            pendingButton = null;
        }
    }

    /** 中立形状。默认实现把这一次派发交回原版，控件命中测试与焦点靠它。 */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MouseButtonEvent e = pendingButton;
        return e != null && super.mouseClicked(e, pendingDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        pendingButton = event;
        try {
            return mouseReleased(event.x(), event.y(), event.button());
        } finally {
            pendingButton = null;
        }
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        MouseButtonEvent e = pendingButton;
        return e != null && super.mouseReleased(e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        pendingButton = event;
        try {
            return mouseDragged(event.x(), event.y(), event.button(), dragX, dragY);
        } finally {
            pendingButton = null;
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        MouseButtonEvent e = pendingButton;
        return e != null && super.mouseDragged(e, dragX, dragY);
    }

    // ==== 键盘：26.x 收 KeyEvent / CharacterEvent ====

    @Override
    public boolean keyPressed(KeyEvent event) {
        pendingKey = event;
        try {
            return keyPressed(event.key(), event.keycode(), event.modifiers());
        } finally {
            pendingKey = null;
        }
    }

    /**
     * 中立形状。{@code keyCode} 对应 {@code KeyEvent.key()}（逻辑键），
     * {@code scanCode} 对应 {@code KeyEvent.keycode()}（物理键）—— 与 1.21.1 那三个
     * 参数按位置对得上，共用代码里比的是 {@code GLFW.GLFW_KEY_*} 那一族常量。
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        KeyEvent e = pendingKey;
        return e != null && super.keyPressed(e);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        pendingKey = event;
        try {
            return keyReleased(event.key(), event.keycode(), event.modifiers());
        } finally {
            pendingKey = null;
        }
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        KeyEvent e = pendingKey;
        return e != null && super.keyReleased(e);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        pendingChar = event;
        try {
            return charTyped((char) event.codepoint(), 0);
        } finally {
            pendingChar = null;
        }
    }

    /**
     * 中立形状。第二个参数在 1.21.1 上是按键修饰位，而 26.x 的
     * {@code CharacterEvent} 只带 {@code codepoint} 一个字段 —— 这里恒传 0。
     * 共用代码里没有一处读它（那 3 处 {@code super.charTyped(c, modifiers)} 只是往回交），
     * 所以这个 0 不改变行为；真要读到修饰位的那天，得让中立形状换成正经的修饰位类型。
     */
    public boolean charTyped(char chr, int modifiers) {
        CharacterEvent e = pendingChar;
        return e != null && super.charTyped(e);
    }

    // ==== 渲染入口：26.x 叫 extractRenderState，共用代码覆写的仍是 render ====

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        render(g, mouseX, mouseY, partialTick);
    }

    /** 中立形状。默认实现把这一帧交回原版（画背景与那一堆可渲染控件）。 */
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    // ==== 尺寸变化：26.x 不再把 Minecraft 递进来 ====

    @Override
    public void resize(int width, int height) {
        resize(this.minecraft, width, height);
    }

    public void resize(Minecraft mc, int width, int height) {
        super.resize(width, height);
    }
}
