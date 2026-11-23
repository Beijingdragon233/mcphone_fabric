package com.november.mcphone.feature.chat.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** C2S：点进会话时请求历史消息；会话列表只带摘要，历史按需拉取。 */
public final class RequestMessagesPacket {
    private final UUID peer;

    public RequestMessagesPacket(UUID peer) {
        this.peer = peer;
    }

    public UUID peer() { return peer; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequestMessagesPacket)) return false;
        RequestMessagesPacket that = (RequestMessagesPacket) other;
        return java.util.Objects.equals(this.peer, that.peer);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(peer);
        return hash;
    }

    @Override public String toString() { return "RequestMessagesPacket[peer=" + peer + "]"; }


    public static void encode(RequestMessagesPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.peer());
    }

    public static RequestMessagesPacket decode(FriendlyByteBuf buf) {
        return new RequestMessagesPacket(
                buf.readUUID());
    }
}
