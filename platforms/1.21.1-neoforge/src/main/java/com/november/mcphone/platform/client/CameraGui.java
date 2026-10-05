package com.november.mcphone.platform.client;

import net.minecraft.client.Minecraft;

/**
 * 相机取景时原版 HUD 怎么藏 —— <b>两支的答案正好相反</b>。
 *
 * <h2>差在哪儿</h2>
 *
 * 这一支（NeoForge）用原版的 {@code hideGui}：{@code RenderGuiEvent} <b>不受它影响</b>，
 * 照样派发，所以取景框画得出来，而准星与物品栏跟着 vanilla 一起藏掉 —— 等效玩家按 F1。
 *
 * Forge 1.20.1 上这条不成立，那一支必须反过来，理由写在它那一份里。
 *
 * <h2>为什么不是一个 if</h2>
 *
 * 值本身只有 true / false，但<b>为什么是这个值</b>要跟着各自的渲染链路走。
 * 写成 shared/ 里的一个 {@code if (是不是 NeoForge)}，那段论证就没有落脚的地方，
 * 而下一个人看到的是一个没有理由的分支。
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

    /** 进入取景时 {@code Minecraft.options.hideGui} 该置成什么。 */
    public static boolean hideGuiWhileFraming() {
        return true;
    }

    /** 此刻原版 HUD 是不是被藏着的。这一支上就是 {@code Minecraft.options.hideGui} 那个字段。 */
    public static boolean hidden(Minecraft mc) {
        return mc.options.hideGui;
    }

    /**
     * 把原版 HUD 藏起来或放出来。
     *
     * <p>这一支是【无条件赋值】：那个字段本身就是状态，写进去即生效。26.x 那一份
     * 不是那样，那边只剩一个 toggle，写之前得先比对 —— 差别与取证记在
     * docs/PORTING-26.3.md。
     */
    public static void setHidden(Minecraft mc, boolean hidden) {
        mc.options.hideGui = hidden;
    }
}
