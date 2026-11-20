package com.november.mcphone.platform;

import com.mojang.serialization.Codec;
import net.minecraft.core.SerializableUUID;

import java.util.UUID;

/** The UUID codecs preserve the NBT and string representations used by newer targets. */
public final class LegacyUuid {
    public static final Codec<UUID> CODEC = SerializableUUID.CODEC;
    public static final Codec<UUID> STRING_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private LegacyUuid() {}
}
