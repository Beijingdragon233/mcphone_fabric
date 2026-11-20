package com.november.mcphone.platform;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/** Factories corresponding to the component factories added after Minecraft 1.18.2. */
public final class LegacyComponents {
    private LegacyComponents() {}

    public static MutableComponent literal(String text) {
        return new TextComponent(text);
    }

    public static MutableComponent translatable(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    public static MutableComponent empty() {
        return new TextComponent("");
    }
}
