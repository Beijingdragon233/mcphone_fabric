package com.november.mcphone.platform.client;

import net.minecraft.client.multiplayer.ClientLevel;

public final class WorldTime {
    private WorldTime() {}

    public static long dayTime(ClientLevel level) {
        return level.getGameTime();
    }
}
