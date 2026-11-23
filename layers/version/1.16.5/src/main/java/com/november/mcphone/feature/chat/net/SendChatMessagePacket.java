package com.november.mcphone.feature.chat.net;

import com.november.mcphone.feature.chat.TextBody;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** C2S：发一条消息。发件人与时间戳都不带：让客户端指定发件人等于允许冒充，时间戳一律以服务端为准。 */
public final class SendChatMessagePacket {
    private final UUID target;
    private final String text;
    private final java.util.Optional<UUID> reply;
    private final java.util.Optional<UUID> ticket;

    public UUID target() { return target; }

    public String text() { return text; }

    public java.util.Optional<UUID> reply() { return reply; }

    public java.util.Optional<UUID> ticket() { return ticket; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SendChatMessagePacket)) return false;
        SendChatMessagePacket that = (SendChatMessagePacket) other;
        return java.util.Objects.equals(this.target, that.target)
                && java.util.Objects.equals(this.text, that.text)
                && java.util.Objects.equals(this.reply, that.reply)
                && java.util.Objects.equals(this.ticket, that.ticket);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(target);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(reply);
        hash = 31 * hash + java.util.Objects.hashCode(ticket);
        return hash;
    }

    @Override public String toString() { return "SendChatMessagePacket[target=" + target + ", text=" + text + ", reply=" + reply + ", ticket=" + ticket + "]"; }

    public SendChatMessagePacket(UUID target, String text) { this(target,text,java.util.Optional.empty()); }
    public SendChatMessagePacket(UUID target, String text, java.util.Optional<UUID> reply) {
        this(target,text,reply,reply.map(ignored -> UUID.randomUUID()));
    }
    public SendChatMessagePacket(UUID target, String text, java.util.Optional<UUID> reply, java.util.Optional<UUID> ticket) {
        java.util.Objects.requireNonNull(target); java.util.Objects.requireNonNull(text);
        java.util.Objects.requireNonNull(reply); java.util.Objects.requireNonNull(ticket);
        if (reply.isPresent() != ticket.isPresent()) throw new IllegalArgumentException("引用和提交票据必须同时存在");
        this.target = target;
        this.text = text;
        this.reply = reply;
        this.ticket = ticket;
    }

    public static void encode(SendChatMessagePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.target());
        buf.writeUtf(msg.text(), TextBody.MAX_LENGTH);
        ChatReplyWire.writeId(buf,msg.reply());
        ChatReplyWire.writeId(buf,msg.ticket());
    }

    public static SendChatMessagePacket decode(FriendlyByteBuf buf) {
        return new SendChatMessagePacket(
                buf.readUUID(),
                buf.readUtf(TextBody.MAX_LENGTH), ChatReplyWire.readId(buf), ChatReplyWire.readId(buf));
    }
}
