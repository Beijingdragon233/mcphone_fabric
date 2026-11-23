package com.november.mcphone.feature.chat.group;

import com.november.mcphone.util.Java8;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.november.mcphone.feature.chat.ChatMessage;

/** 群内递增序号与发送时的名称；成员退出后，旧消息仍有正确的头像 UUID 和名称。 */
public final class GroupMessage {
    private final long sequence;
    private final String senderName;
    private final ChatMessage message;

    public long sequence() { return sequence; }

    public String senderName() { return senderName; }

    public ChatMessage message() { return message; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GroupMessage)) return false;
        GroupMessage that = (GroupMessage) other;
        return this.sequence == that.sequence
                && java.util.Objects.equals(this.senderName, that.senderName)
                && java.util.Objects.equals(this.message, that.message);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sequence);
        hash = 31 * hash + java.util.Objects.hashCode(senderName);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }

    @Override public String toString() { return "GroupMessage[sequence=" + sequence + ", senderName=" + senderName + ", message=" + message + "]"; }

    public GroupMessage(long sequence, String senderName, ChatMessage message) {
        java.util.Objects.requireNonNull(message);
        if (sequence <= 0 || senderName == null || Java8.isBlank(senderName)
                || senderName.length() > GroupLimits.NAME_LENGTH) throw new GroupFault(GroupStatus.INVALID);
        this.sequence = sequence;
        this.senderName = senderName;
        this.message = message;
    }
    public static final Codec<GroupMessage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("sequence").forGetter(GroupMessage::sequence),
            Codec.STRING.fieldOf("sender_name").forGetter(GroupMessage::senderName),
            ChatMessage.CODEC.fieldOf("message").forGetter(GroupMessage::message)).apply(i, GroupMessage::new));
}
