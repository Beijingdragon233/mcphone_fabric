package com.november.mcphone.compat;

import com.november.mcphone.feature.music.NetSong;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * NetMusic capability facade for 26.3.
 *
 * <p>NetMusic has no 26.3 NeoForge build, so its direct-API implementation is
 * excluded from the mounted source set. This facade keeps the music call sites
 * stable and reports the integration as unavailable instead of pretending a
 * CD was read successfully.</p>
 */
public final class NetMusicCompat {

    public static final String NETMUSIC_MODID = "netmusic";

    private NetMusicCompat() {}

    public static boolean isLoaded() {
        return false;
    }

    public static Optional<NetSong> songOf(ItemStack stack) {
        return Optional.empty();
    }
}
