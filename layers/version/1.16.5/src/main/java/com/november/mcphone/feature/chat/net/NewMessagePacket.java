package com.november.mcphone.feature.chat.net;

import net.minecraft.network.FriendlyByteBuf;
import com.november.mcphone.feature.chat.ChatMessage;

import java.util.UUID;

/**
 * S2C：来了一条新消息。只推这一条，不重发整个列表；收发双方都收，发件人靠回声显示自己那条。
 * peer 站在收包方的角度是会话对端：收件人收到时是发件人，发件人收到回声时是收件人。
 */
public final class NewMessagePacket {
    private final UUID peer;
    private final ChatMessage message;

    public NewMessagePacket(UUID peer, ChatMessage message) {
        this.peer = peer;
        this.message = message;
    }

    public UUID peer() { return peer; }

    public ChatMessage message() { return message; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NewMessagePacket)) return false;
        NewMessagePacket that = (NewMessagePacket) other;
        return java.util.Objects.equals(this.peer, that.peer)
                && java.util.Objects.equals(this.message, that.message);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(peer);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }

    @Override public String toString() { return "NewMessagePacket[peer=" + peer + ", message=" + message + "]"; }


    public static void encode(NewMessagePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.peer());
        ChatMessage.encode(msg.message(), buf);
    }

    public static NewMessagePacket decode(FriendlyByteBuf buf) {
        return new NewMessagePacket(
                buf.readUUID(),
                ChatMessage.decode(buf));
    }
}
