package com.november.mcphone.feature.chat.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** C2S：传送到某个好友面前。故意只带目标 UUID：坐标、维度与全部校验都由服务端现查，客户端报什么都不采信。 */
public final class TeleportToFriendPacket {
    private final UUID target;

    public TeleportToFriendPacket(UUID target) {
        this.target = target;
    }

    public UUID target() { return target; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TeleportToFriendPacket)) return false;
        TeleportToFriendPacket that = (TeleportToFriendPacket) other;
        return java.util.Objects.equals(this.target, that.target);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(target);
        return hash;
    }

    @Override public String toString() { return "TeleportToFriendPacket[target=" + target + "]"; }


    public static void encode(TeleportToFriendPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.target());
    }

    public static TeleportToFriendPacket decode(FriendlyByteBuf buf) {
        return new TeleportToFriendPacket(
                buf.readUUID());
    }
}
