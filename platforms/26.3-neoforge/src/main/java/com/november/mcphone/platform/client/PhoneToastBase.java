package com.november.mcphone.platform.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

/**
 * 手机这一族通知的基类 —— 它存在的理由是【26.x 把 {@code Toast} 那一个回调拆成了三个】。
 *
 * <h2>差在哪儿</h2>
 *
 * 老那三支上 {@code Toast} 只有一个抽象方法 {@code render(GuiGraphics, ToastComponent, long)}：
 * <b>一边画、一边决定这条通知还留不留</b>，留下的那份就是它的返回值。这一支把它拆成三步 ——
 * {@code update(ToastManager, long)} 里【只决定】（决定完放在那儿等
 * {@code getWantedVisibility()} 来取），{@code extractRenderState(GuiGraphicsExtractor, Font, long)}
 * 里【只画】，进出场的音效由 {@code ToastManager} 按 {@code getWantedVisibility()} 与前一次的
 * 差放，不再挂在渲染的返回值上。
 *
 * <h2>所以中立形状是两个方法，不是一个</h2>
 *
 * 判据同 {@link PhoneScreenBase}：这是【覆写点】变了，门面改不了「一个方法长什么样」，
 * 只能由每支一份的基类替子类覆写掉原版那几个，再转调一组两支同形的中立方法。
 * 但这一族连原版的回调本身都是两支不同形的，所以中立方法只能照【拆得开的那一支】起两个名字 ——
 * 这里没有「照 1.21.1 的形状起一个名字」那条路可走。
 *
 * <h2>两支的调用次序不同，为什么结果仍然一样</h2>
 *
 * 这一支是【先画、再决定】；26.x 是【先决定（在 tick 里）、随后那一帧才画】。
 * 之所以不打架：画的那一半不读停留计时，决定的那一半自己把计时重置掉 ——
 * 「有新消息并进来就重新计时」这件事必须放在【决定】那一半里，两支都在同一帧内生效。
 * 放在画的那一半里，这一支上就会晚一步，症状是并进来那一帧通知先滑出去半格。
 *
 * <h2>为什么 wanted 要初值 SHOW</h2>
 *
 * {@code ToastManager} 每帧先调实例的 {@code update} 再取 {@code getWantedVisibility()}，
 * 所以正常路径上这里读到的总是刚写过的那一个。字段只在【没被 update 过】时才可能露出来，
 * 而那个窗口里的动画进度由 {@code ToastInstance} 自己兜着，不认这个值。
 */
public abstract class PhoneToastBase implements Toast {

    private Visibility wanted = Visibility.SHOW;

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        this.wanted = shouldStillShow(fullyVisibleForMs, manager.getNotificationDisplayTimeMultiplier())
                ? Visibility.SHOW
                : Visibility.HIDE;
    }

    @Override
    public Visibility getWantedVisibility() {
        return wanted;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, Font font, long fullyVisibleForMs) {
        drawToast(g, font, fullyVisibleForMs);
    }

    /**
     * 只画，不决定去留。
     *
     * <p>{@code font} 在这一支上是原版直接递进来的形参（{@code ToastManager} 取的是
     * {@code Minecraft#font}），老那三支是从管理器身上拿的 —— 同一个字体对象。
     */
    protected abstract void drawToast(GuiGraphicsExtractor g, Font font, long timeSinceLastVisible);

    /**
     * 这一条还该留着，还是该收起来。
     *
     * <p>{@code displayMultiplier} 是玩家在设置里调的通知停留时长倍率：这一支的
     * {@code ToastManager} 与老那个都有这个口，共用代码只管拿它乘自己的停留时长。
     */
    protected abstract boolean shouldStillShow(long timeSinceLastVisible, double displayMultiplier);
}
