package com.november.mcphone.platform.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.sdl.SDLScancode;

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
 * 而 26.x 把原版那三个查询从 {@code Screen} 的静态方法搬成了 {@code Minecraft} 的实例方法，
 * 四支必须有同一个名字的出口 —— macOS 那条换键的取舍写在各自那份里。
 */
public final class KeyModifiers {

    private KeyModifiers() {}

    public static InputConstants.Type keyboardType() {
        return InputConstants.Type.KEYBOARD;
    }

    public static InputConstants.Key fromKeyCodes(int key, int keycode) {
        return InputConstants.getKey(new KeyEvent(key, keycode, 0));
    }

    public static boolean matches(KeyMapping mapping, int key, int keycode) {
        return mapping.matches(new KeyEvent(key, keycode, 0));
    }

    /** 这个键本身是不是一个修饰键（Ctrl / Shift / Alt）。 */
    public static boolean isModifierKey(InputConstants.Key key) {
        return KeyModifier.isKeyCodeModifier(key);
    }

    // ==== 第二组：此刻按着哪个修饰键 ====

    /**
     * Ctrl 有没有按着；macOS 上认的是 Command —— 与老那三支 {@code Screen.hasControlDown()}
     * 同语义，但【这一支那个原版方法本身不换键】，得在这里补。
     *
     * <h3>为什么要补</h3>
     *
     * 26.x 把这三个查询从 {@code Screen} 的静态方法搬成了 {@code Minecraft} 的实例方法
     * （{@code Minecraft.java:778 hasShiftDown}、{@code :782 hasControlDown}、
     * {@code :786 hasAltDown}），而搬过来的那一份查的就是普通左/右 Ctrl：
     * {@code isKeyDown(224) || isKeyDown(228)}，【没有】{@code ON_OSX} 那个分支。直接用它，
     * macOS 上「按住 Ctrl 滚轮调悬浮 HUD 大小」和「浏览器页 Ctrl+滚轮缩放」都不再触发 ——
     * 那是行为变化，不是改名。原版也没把这条规矩丢掉：{@code InputQuirks} 里
     * {@code REPLACE_CTRL_KEY_WITH_CMD_KEY}（{@code InputQuirks.java:15}，它就是 {@code ON_OSX}）
     * 与 {@code EDIT_SHORTCUT_KEY_MODIFIER}（{@code :16}，macOS 上是 3072 = 左右 GUI 那两个修饰位）
     * 就是它的出处，事件那一侧 {@code InputWithModifiers#hasControlDownWithQuirk()}
     * （{@code InputWithModifiers.java:65-67}）用的正是这两个常量。这里要的是「此刻的状态」
     * 而不是「这个事件带没带」，滚轮回调里没有事件对象，所以查键盘状态。
     *
     * <h3>为什么参数是 scancode</h3>
     *
     * {@code InputConstants.isKeyDown(int)}（{@code InputConstants.java:220-223}）读的是
     * {@code SDL_GetKeyboardState()[key]} —— 这一支的窗口后端是 SDL，参数是 <b>scancode</b>。
     * {@code Minecraft.hasControlDown} 那对 224 / 228 正是 {@code SDL_SCANCODE_LCTRL} /
     * {@code SDL_SCANCODE_RCTRL}，这条对得上，Command 就取 {@code SDL_SCANCODE_LGUI} /
     * {@code SDL_SCANCODE_RGUI}（lwjgl-sdl 3.4.3 里是 227 / 231）。
     */
    public static boolean ctrl() {
        return InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY
                ? InputConstants.isKeyDown(SDLScancode.SDL_SCANCODE_LGUI)
                        || InputConstants.isKeyDown(SDLScancode.SDL_SCANCODE_RGUI)
                : Minecraft.getInstance().hasControlDown();
    }

    /** Shift 有没有按着：老三支查 GLFW 340 / 344，这一支查 SDL 225 / 229，同一对物理键。 */
    public static boolean shift() {
        return Minecraft.getInstance().hasShiftDown();
    }

    /** Alt 有没有按着：老三支 342 / 346，这一支 226 / 230。 */
    public static boolean alt() {
        return Minecraft.getInstance().hasAltDown();
    }
}
