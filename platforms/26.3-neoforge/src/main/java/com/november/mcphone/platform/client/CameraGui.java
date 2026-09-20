package com.november.mcphone.platform.client;

import net.minecraft.client.Minecraft;

/**
 * 相机取景时原版 HUD 怎么藏 —— <b>四支里这一支跟 1.21.1 那两支同一边</b>。
 *
 * <h2>那个状态在这支上换了住处</h2>
 *
 * 1.20.1 与 1.21.1 读写的 {@code Minecraft.options.hideGui}，在 26.x <b>整个字段没了</b>：
 * 整棵 26.3 反编译源码（7301 个 java）里 {@code hideGui} 只剩 {@code ScreenEffectRenderer}
 * 一个文件里的方法参数名。状态搬进了 {@code Hud} —— {@code Hud.java:156} 一个 private
 * {@code boolean isHidden}，往外只给 {@code isHidden()}（{@code :222}）与 {@code toggle()}
 * （{@code :218}），<b>没有 setter</b>。所以下面那句"写"只能先看当前值、不等才翻；
 * 老那一支那种直接赋值，在这支上根本没有可以赋的东西。
 *
 * <p>原版自己藏 HUD 也改走这条口：F1 现在是 {@code Options.keyToggleGui}
 * （{@code Options.java:694}）→ {@code Gui#handleKeybinds}（{@code Gui.java:341}）
 * → {@code this.hud.toggle()}。这一层没有自创第二条路。
 *
 * <h2>取景时该藏（true）</h2>
 *
 * 决定这个值的还是那句老问法：我们那层画在 {@code RenderGuiEvent.Post} 上，它受不受
 * 这个状态影响。26.x 是<b>各层自己带关卡</b> —— {@code Hud} 注册层时逐个挂上
 * {@code hudVisible}（{@code Hud.java:243} 那句 {@code () -> !this.isHidden}，
 * 用在 {@code :258-269} 那串 {@code add} 上），而 NeoForge 的
 * {@code RenderGuiEvent.Pre} / {@code Post} 在层循环<b>外面</b>那两句
 * （{@code GuiLayerManager.java:63-71}）；{@code GameRenderer} 那边也是无条件调
 * {@code gui.extractRenderState}（{@code GameRenderer.java:460}，它只按
 * {@code shouldRenderLevel} 决定要不要 hud 那一段，{@code Gui.java:156}）。
 * 所以藏起来之后准星与物品栏跟着 vanilla 一起没，取景框照画 —— 等效玩家按 F1，
 * 与 1.21.1-neoforge 同一形状。
 *
 * <p>Forge 1.20.1 那条「{@code hideGui} 一真，整个 {@code gui.render} 被跳过、事件根本
 * 不发」的坑在这支不存在，那一支被迫返回 false 的理由写在那一份里。
 *
 * <h2>这一层现在也管【读写那个隐藏状态】</h2>
 *
 * 「此刻原版 HUD 是不是藏着的、要藏要露该怎么写」在四个目标上是两样东西：
 * 1.20.1 与 1.21.1 这两代是 {@code Minecraft.options.hideGui} 那个 public 字段，
 * 26.x 那个字段整个没了、状态改住在 {@code Hud} 里（取证在各份自己那节）。
 * 让调用方各写各的，同一件事就有四份读法，而其中一份已经在 26.3 上被编译器挑出来了。
 * 所以 {@code CameraMode} 存与还那两下、{@code PhoneHud} 那句「F1 时不画手机」，
 * 都只问这一层。
 *
 * <p>{@code PhoneHud} 问这一层不是为了自己编得过 —— 它是【每个目标各有一份】的文件，
 * 三支老的本来写得通。收进来的理由是那一行在四份里保持逐字相同。
 */
public final class CameraGui {

    private CameraGui() {}

    /** 进入取景时原版 HUD 该不该藏起来。这一支上那个状态不在 {@code Options} 里，见上。 */
    public static boolean hideGuiWhileFraming() {
        return true;
    }

    /** 此刻原版 HUD 是不是被藏着的 —— 这一支上它是 {@code Hud} 的状态，不在 {@code Options} 里。 */
    public static boolean hidden(Minecraft mc) {
        return mc.gui.hud.isHidden();
    }

    /**
     * 把原版 HUD 藏起来或放出来。
     *
     * <p>【先比对再翻】不是风格问题：这支只有 {@code toggle()}，无条件调用等于「替玩家
     * 按一次 F1」，而调用方要的是「置成这个值」。
     */
    public static void setHidden(Minecraft mc, boolean hidden) {
        if (mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
    }
}
