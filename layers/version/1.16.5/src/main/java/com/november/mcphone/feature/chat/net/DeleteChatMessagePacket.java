package com.november.mcphone.feature.chat.net;

import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

/** 个人删除的请求/确认；所有者来自连接身份，票据约束客户端响应归属。 */
public final class DeleteChatMessagePacket {
    private final UUID peer;
    private final UUID messageId;
    private final UUID requestId;

    public DeleteChatMessagePacket(UUID peer, UUID messageId, UUID requestId) {
        this.peer = peer;
        this.messageId = messageId;
        this.requestId = requestId;
    }

    public UUID peer() { return peer; }

    public UUID messageId() { return messageId; }

    public UUID requestId() { return requestId; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DeleteChatMessagePacket)) return false;
        DeleteChatMessagePacket that = (DeleteChatMessagePacket) other;
        return java.util.Objects.equals(this.peer, that.peer)
                && java.util.Objects.equals(this.messageId, that.messageId)
                && java.util.Objects.equals(this.requestId, that.requestId);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(peer);
        hash = 31 * hash + java.util.Objects.hashCode(messageId);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        return hash;
    }

    @Override public String toString() { return "DeleteChatMessagePacket[peer=" + peer + ", messageId=" + messageId + ", requestId=" + requestId + "]"; }

    public static void encode(DeleteChatMessagePacket msg, FriendlyByteBuf buf) { buf.writeUUID(msg.peer()); buf.writeUUID(msg.messageId()); buf.writeUUID(msg.requestId()); }
    public static DeleteChatMessagePacket decode(FriendlyByteBuf buf) { return new DeleteChatMessagePacket(buf.readUUID(), buf.readUUID(), buf.readUUID()); }
}
