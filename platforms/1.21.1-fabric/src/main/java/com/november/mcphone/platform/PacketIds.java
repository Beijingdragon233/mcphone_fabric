package com.november.mcphone.platform;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class PacketIds {
    private PacketIds() {}

    public static void write(FriendlyByteBuf buf, ResourceLocation id) {
        buf.writeResourceLocation(id);
    }

    public static ResourceLocation read(FriendlyByteBuf buf) {
        return buf.readResourceLocation();
    }
}
