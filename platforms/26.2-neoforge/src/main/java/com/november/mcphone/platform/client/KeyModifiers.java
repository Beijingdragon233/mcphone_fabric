package com.november.mcphone.platform.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * 修饰键相关的判断。
 *
 * <h2>为什么要有这个门面</h2>
 *
 * {@code KeyModifier} 是<b>加载器的类型</b>（两支的包名不一样），原版没有对应物。
 * 只要某个类里出现这个名字，它就只能留在各平台自己那一份里。
 *
 * <h2>为什么只搬了这一个方法</h2>
 *
 * {@code AppManagerDetail} 用到 {@code KeyModifier} 的地方<b>只有这一处</b>，
 * 收进来它整份（五百多行）就能进共用层。
 *
 * {@code AppHotkeys} 是另一回事：那个类把 {@code Set<KeyModifier>} 摆进了自己的数据
 * 模型（{@code record Binding}）、存盘格式与冲突判定里，要脱钩得先换掉那个类型参数——
 * 那是一次真重构而不是加个门面，而它只有两百多行。<b>没有做，也不该拿这个门面硬套。</b>
 *
 * <h2>第二组：此刻按着哪个修饰键</h2>
 *
 * 下面那三个 {@code ctrl()} / {@code shift()} / {@code alt()} 与上面那个加载器类型无关，
 * 它们回答的是「这一帧有没有按着某个修饰键」。放在一起是因为这就是同一件事的两半，
 * 而 26.2 已删掉 {@code Screen} 的静态查询，又还没切到 SDL scancode；
 * 因此这里直接查 GLFW，macOS 仍按 Command 算 Ctrl。
 */
public final class KeyModifiers {

    private KeyModifiers() {}

    public static InputConstants.Type keyboardType() {
        return InputConstants.Type.KEYSYM;
    }

    public static InputConstants.Key fromKeyCodes(int key, int scanCode) {
        return InputConstants.getKey(new KeyEvent(key, scanCode, 0));
    }

    public static boolean matches(KeyMapping mapping, int key, int scanCode) {
        return mapping.matches(new KeyEvent(key, scanCode, 0));
    }

    /** 这个键本身是不是一个修饰键（Ctrl / Shift / Alt）。 */
    public static boolean isModifierKey(InputConstants.Key key) {
        return KeyModifier.isKeyCodeModifier(key);
    }

    // ==== 第二组：此刻按着哪个修饰键 ====

    /**
     * 26.2 已移除 {@code Screen} 上的静态查询，但尚未把 SDL scancode 接进
     * {@code InputConstants.isKeyDown}；这里直接查 GLFW 键码。
     */
    public static boolean ctrl() {
        boolean mac = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("mac");
        return mac ? down(GLFW.GLFW_KEY_LEFT_SUPER) || down(GLFW.GLFW_KEY_RIGHT_SUPER)
                : down(GLFW.GLFW_KEY_LEFT_CONTROL) || down(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    /** Shift 有没有按着。 */
    public static boolean shift() {
        return down(GLFW.GLFW_KEY_LEFT_SHIFT) || down(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /** Alt 有没有按着。 */
    public static boolean alt() {
        return down(GLFW.GLFW_KEY_LEFT_ALT) || down(GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private static boolean down(int key) {
        return GLFW.glfwGetKey(
                net.minecraft.client.Minecraft.getInstance().getWindow().handle(), key)
                == GLFW.GLFW_PRESS;
    }
}
