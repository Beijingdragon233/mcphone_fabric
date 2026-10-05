package com.november.mcphone.platform.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.november.mcphone.core.client.KeyModifier;
import net.minecraft.client.gui.screens.Screen;

/**
 * 「这个键是不是修饰键」的门面。NeoForge 那一支问的是它自带的
 * {@code KeyModifier}；Fabric 没有这号人物，答话的是我们复刻的
 * {@link KeyModifier} 枚举 —— 语义与 NeoForge 逐字对齐，见它的类注释。
 *
 * <h2>第二组：此刻按着哪个修饰键</h2>
 *
 * 下面那三个 {@code ctrl()} / {@code shift()} / {@code alt()} 与上面那个加载器类型无关，
 * 它们回答的是「这一帧有没有按着某个修饰键」。放在一起是因为这就是同一件事的两半，
 * 而 26.x 把原版那三个查询从 {@code Screen} 的静态方法搬成了 {@code Minecraft} 的实例方法，
 * 四支必须有同一个名字的出口 —— macOS 那条换键的取舍写在各自那份里。
 */
public final class KeyModifiers {

    private KeyModifiers() {}

    /** 这个键本身是不是一个修饰键（Ctrl / Shift / Alt）。 */
    public static boolean isModifierKey(InputConstants.Key key) {
        return KeyModifier.isKeyCodeModifier(key);
    }

    // ==== 第二组：此刻按着哪个修饰键 ====

    /**
     * Ctrl 有没有按着；macOS 上认的是 Command。
     *
     * <p>这一支上它就是原版 {@code Screen.hasControlDown()}：{@code Screen.java:425-431} 里
     * {@code Minecraft.ON_OSX} 为真时查 LEFT_SUPER / RIGHT_SUPER（343 / 347），否则查
     * LEFT_CONTROL / RIGHT_CONTROL（341 / 345）。共用代码里「按住 Ctrl 滚轮调悬浮 HUD 大小」
     * 与「浏览器页 Ctrl+滚轮缩放」都认这一条，所以 26.3 那一份得自己把这个换键补上 ——
     * 那一支的原版方法只查普通 Ctrl，照搬会在 mac 上静默失灵，取舍写在它自己那份里。
     */
    public static boolean ctrl() {
        return Screen.hasControlDown();
    }

    /** Shift 有没有按着（{@code Screen.java:433-436}，340 / 344，没有平台差异）。 */
    public static boolean shift() {
        return Screen.hasShiftDown();
    }

    /** Alt 有没有按着（{@code Screen.java:438-441}，342 / 346，没有平台差异）。 */
    public static boolean alt() {
        return Screen.hasAltDown();
    }
}
