package com.november.mcphone.platform;

import net.minecraft.SharedConstants;

public final class WorldVersions {
    private WorldVersions() {}

    public static String currentName() {
        return SharedConstants.getCurrentVersion().name();
    }
}
