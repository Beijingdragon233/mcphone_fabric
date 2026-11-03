package com.november.mcphone.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.sdl.SDLScancode;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 修饰键（Ctrl / Shift / Alt）的语义工具。
 *
 * Fabric 没有 NeoForge 那个 KeyModifier（客户端设置包里），而 AppHotkeys 的
 * 组合键逻辑整套建立在它上面：Mac 上把 Command 算成 Ctrl、修饰键名字的本地化、
 * "一个键是不是修饰键本体"。所以这里按同样的语义复刻一份，只实现本项目用到的
 * 那几个面，别的地方不抄。
 */
public enum KeyModifier {
    NONE, SHIFT, CONTROL, ALT;

    /** 固定顺序：序列化、遍历都按它走，文件才不会无谓地变 */
    public static final KeyModifier[] MODIFIER_VALUES = { SHIFT, CONTROL, ALT };

    /** 从一个名字读。读不出来（含 null/空）一律 NONE，由调用方决定怎么办 */
    public static KeyModifier valueFromString(String value) {
        if (value == null) return NONE;
        for (KeyModifier m : values()) {
            if (m.name().equalsIgnoreCase(value.trim())) return m;
        }
        return NONE;
    }

    /**
     * 此刻按住的所有修饰键。
     *
     * 26.3 的输入后端是 SDL；macOS 上 Command 仍按原版
     * {@link InputQuirks#REPLACE_CTRL_KEY_WITH_CMD_KEY} 的语义算成 CONTROL。
     */
    public static Set<KeyModifier> getActiveModifiers() {
        EnumSet<KeyModifier> out = EnumSet.noneOf(KeyModifier.class);
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return out;

        if (mc.hasShiftDown()) {
            out.add(SHIFT);
        }
        boolean control = InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY
                ? InputConstants.isKeyDown(SDLScancode.SDL_SCANCODE_LGUI)
                        || InputConstants.isKeyDown(SDLScancode.SDL_SCANCODE_RGUI)
                : mc.hasControlDown();
        if (control) out.add(CONTROL);
        if (mc.hasAltDown()) {
            out.add(ALT);
        }
        return out;
    }

    /** 从一次按键事件的修饰位算出按住的那几个。26.3 的修饰位仍是位掩码。 */
    public static Set<KeyModifier> fromModifiers(int modifiers) {
        return getActiveModifiers();
    }

    /** 这个键是不是某个修饰键本体（Ctrl/Shift/Alt，Mac 上还包括 Command） */
    public static boolean isKeyCodeModifier(InputConstants.Key key) {
        int v = key.getValue();
        return v == SDLScancode.SDL_SCANCODE_LSHIFT || v == SDLScancode.SDL_SCANCODE_RSHIFT
                || v == SDLScancode.SDL_SCANCODE_LCTRL || v == SDLScancode.SDL_SCANCODE_RCTRL
                || v == SDLScancode.SDL_SCANCODE_LALT || v == SDLScancode.SDL_SCANCODE_RALT
                || v == SDLScancode.SDL_SCANCODE_LGUI || v == SDLScancode.SDL_SCANCODE_RGUI;
    }

    /** "Ctrl + K" 那种显示名，修饰键名走语言文件，跟着玩家的语言走 */
    public Component getCombinedName(InputConstants.Key key, Supplier<Component> defaultKeyname) {
        String langKey = "key.mcphone.modifier." + name().toLowerCase(Locale.ROOT);
        MutableComponent name = Component.translatable(langKey, defaultKeyname.get());
        return name;
    }
}
