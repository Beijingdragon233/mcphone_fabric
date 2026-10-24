package com.november.mcphone.platform;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

public final class PacketIds {
    private PacketIds() {}

    public static void write(FriendlyByteBuf buf, Identifier id) {
        buf.writeIdentifier(id);
    }

    public static Identifier read(FriendlyByteBuf buf) {
        return buf.readIdentifier();
    }
}
