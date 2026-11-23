package com.november.mcphone.feature.chat.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** C2S：同意（accept=true）或拒绝一条好友申请，两者校验相同所以合成一个包；两种情况都会消掉申请。 */
public final class RespondFriendRequestPacket {
    private final UUID requester;
    private final boolean accept;

    public RespondFriendRequestPacket(UUID requester, boolean accept) {
        this.requester = requester;
        this.accept = accept;
    }

    public UUID requester() { return requester; }

    public boolean accept() { return accept; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RespondFriendRequestPacket)) return false;
        RespondFriendRequestPacket that = (RespondFriendRequestPacket) other;
        return java.util.Objects.equals(this.requester, that.requester)
                && this.accept == that.accept;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requester);
        hash = 31 * hash + java.util.Objects.hashCode(accept);
        return hash;
    }

    @Override public String toString() { return "RespondFriendRequestPacket[requester=" + requester + ", accept=" + accept + "]"; }


    public static void encode(RespondFriendRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.requester());
        buf.writeBoolean(msg.accept());
    }

    public static RespondFriendRequestPacket decode(FriendlyByteBuf buf) {
        return new RespondFriendRequestPacket(
                buf.readUUID(),
                buf.readBoolean());
    }
}
