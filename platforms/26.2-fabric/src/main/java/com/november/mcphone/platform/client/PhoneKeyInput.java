package com.november.mcphone.platform.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** 键码与扫描码的版本接缝。 */
public final class PhoneKeyInput {
    private PhoneKeyInput() {}

    public static InputConstants.Key key(int code, int scan) {
        return KeyModifiers.fromKeyCodes(code, scan);
    }

    public static boolean matches(KeyMapping key, int code, int scan) {
        return KeyModifiers.matches(key, code, scan);
    }
}
