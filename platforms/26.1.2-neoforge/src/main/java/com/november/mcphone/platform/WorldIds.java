package com.november.mcphone.platform;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class WorldIds {
    private WorldIds() {}

    public static String dimensionId(ResourceKey<Level> key) {
        return key.identifier().toString();
    }
}
