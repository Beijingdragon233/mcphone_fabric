package com.november.mcphone.feature.music.net;

import net.minecraft.network.FriendlyByteBuf;

/** S2C：某个人的手机把网络音乐停了。按实体停，比原版按音效 ID 的 ClientboundStopSoundPacket 停得准 */
public final class StopNetSongPacket {
    private final int entityId;

    public StopNetSongPacket(int entityId) {
        this.entityId = entityId;
    }

    public int entityId() { return entityId; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StopNetSongPacket)) return false;
        StopNetSongPacket that = (StopNetSongPacket) other;
        return this.entityId == that.entityId;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(entityId);
        return hash;
    }

    @Override public String toString() { return "StopNetSongPacket[entityId=" + entityId + "]"; }


    public static void encode(StopNetSongPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId());
    }

    public static StopNetSongPacket decode(FriendlyByteBuf buf) {
        return new StopNetSongPacket(
                buf.readVarInt());
    }
}
