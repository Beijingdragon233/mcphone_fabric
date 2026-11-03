package com.november.mcphone.platform.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

public final class WeatherValues {
    private WeatherValues() {}

    public static Biome.Precipitation precipitation(ClientLevel level, BlockPos pos) {
        return level.getPrecipitationAt(pos);
    }
}
