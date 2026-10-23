package com.november.mcphone.platform.client;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;

/** 26.3 的唱片曲目直接存于物品数据组件。 */
public final class DiscSongs {
    private DiscSongs() {}

    public static String title(ItemStack disc) {
        return JukeboxSong.fromStack(disc)
                .map(song -> song.value().description().getString())
                .orElseGet(() -> disc.getHoverName().getString());
    }
}
