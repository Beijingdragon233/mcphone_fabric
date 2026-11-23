package com.november.mcphone.compat;

import com.november.mcphone.MCphone;
import com.november.mcphone.platform.ModPresence;
import net.minecraft.server.level.ServerPlayer;

public final class WaystonesCompat {
    public static final String WAYSTONES_MODID = "waystones";

    private WaystonesCompat() {}

    public static boolean isLoaded() {
        return ModPresence.isLoaded(WAYSTONES_MODID);
    }

    public static boolean openSelection(ServerPlayer player) {
        if (!isLoaded()) return false;
        try {
            return Waystones7.openSelection(player);
        } catch (LinkageError | RuntimeException e) {
            MCphone.LOGGER.error("Unable to open Waystones 7 selection", e);
            return false;
        }
    }
}
