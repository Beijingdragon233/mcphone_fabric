package com.november.mcphone.platform.client.port;

import com.mojang.blaze3d.platform.InputConstants;
import com.november.mcphone.platform.client.KeyModifiers;
import com.november.mcphone.platform.client.PhoneScreenBase;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** 新旧输入事件之间的适配状态。 */
public final class PhoneInputs {
    public static CharacterEvent character;
    public static MouseButtonEvent mouse;
    public static boolean doubleClick;

    private PhoneInputs() {}

    public static boolean down(int scancode) {
        return org.lwjgl.glfw.GLFW.glfwGetKey(
                net.minecraft.client.Minecraft.getInstance().getWindow().handle(), scancode)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    public static boolean shift() {
        return KeyModifiers.shift();
    }

    public static boolean control() {
        return KeyModifiers.ctrl();
    }

    public static boolean alt() {
        return KeyModifiers.alt();
    }

    public static boolean isCopy(int key) {
        return control() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_C;
    }

    public static int modifiers() {
        return 0;
    }

    public static MouseButtonEvent mouse(double x, double y, int pageButton) {
        return new MouseButtonEvent(x, y,
                new MouseButtonInfo(PhoneScreenBase.nativeButton(pageButton), modifiers()));
    }

    public static CharacterEvent character(char c) {
        return character == null ? new CharacterEvent(c) : character;
    }

    public static boolean dispatchCharacter(int codePoint, java.util.function.IntPredicate typed) {
        if (!Character.isValidCodePoint(codePoint)) return false;
        if (character != null) {
            return typed.test(Character.isSupplementaryCodePoint(codePoint)
                    ? Character.highSurrogate(codePoint) : (char) codePoint);
        }
        if (Character.isBmpCodePoint(codePoint)) return typed.test((char) codePoint);
        boolean first = typed.test(Character.highSurrogate(codePoint));
        return typed.test(Character.lowSurrogate(codePoint)) | first;
    }

    public static boolean submitCharacter(char unit,
                                          java.util.function.Predicate<CharacterEvent> receiver) {
        if (character != null && Character.isSupplementaryCodePoint(character.codepoint())
                && unit == Character.lowSurrogate(character.codepoint())) return false;
        return receiver.test(character(unit));
    }
}
