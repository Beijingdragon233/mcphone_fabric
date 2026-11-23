package com.november.mcphone.feature.chat.net;

import com.november.mcphone.feature.chat.ChatDeletionResult;
import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

/** 个人删除的请求/确认；所有者来自连接身份，票据约束客户端响应归属。 */
public final class ChatMessageDeleteResultPacket {
    private final UUID peer;
    private final UUID messageId;
    private final UUID requestId;
    private final ChatDeletionResult result;

    public ChatMessageDeleteResultPacket(UUID peer, UUID messageId, UUID requestId, ChatDeletionResult result) {
        this.peer = peer;
        this.messageId = messageId;
        this.requestId = requestId;
        this.result = result;
    }

    public UUID peer() { return peer; }

    public UUID messageId() { return messageId; }

    public UUID requestId() { return requestId; }

    public ChatDeletionResult result() { return result; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChatMessageDeleteResultPacket)) return false;
        ChatMessageDeleteResultPacket that = (ChatMessageDeleteResultPacket) other;
        return java.util.Objects.equals(this.peer, that.peer)
                && java.util.Objects.equals(this.messageId, that.messageId)
                && java.util.Objects.equals(this.requestId, that.requestId)
                && java.util.Objects.equals(this.result, that.result);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(peer);
        hash = 31 * hash + java.util.Objects.hashCode(messageId);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(result);
        return hash;
    }

    @Override public String toString() { return "ChatMessageDeleteResultPacket[peer=" + peer + ", messageId=" + messageId + ", requestId=" + requestId + ", result=" + result + "]"; }

    public static void encode(ChatMessageDeleteResultPacket msg, FriendlyByteBuf buf) { buf.writeUUID(msg.peer()); buf.writeUUID(msg.messageId()); buf.writeUUID(msg.requestId()); buf.writeVarInt(msg.result().wireId()); }
    public static ChatMessageDeleteResultPacket decode(FriendlyByteBuf buf) { return new ChatMessageDeleteResultPacket(buf.readUUID(), buf.readUUID(), buf.readUUID(), decodeResult(buf.readVarInt())); }
    public static ChatDeletionResult decodeResult(int ordinal) {
        return ChatDeletionResult.fromWire(ordinal);
    }
}
