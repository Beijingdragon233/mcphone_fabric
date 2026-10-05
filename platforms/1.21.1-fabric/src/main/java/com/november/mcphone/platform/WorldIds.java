package com.november.mcphone.platform;

import net.minecraft.world.level.Level;

public final class WorldIds {
    private WorldIds() {}
    public static String dimension(Level level) { return level.dimension().location().toString(); }
}
