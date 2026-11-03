package com.november.mcphone.platform.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

/** 原版降水查询的版本接缝。 */
public final class PhoneWeather {
    private PhoneWeather() {}

    public static Biome.Precipitation precipitation(ClientLevel level, BlockPos pos) {
        return WeatherValues.precipitation(level, pos);
    }
}
