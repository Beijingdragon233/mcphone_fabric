package com.november.mcphone.feature.chat.group.net;
import com.november.mcphone.feature.chat.ChatMessage;
import net.minecraft.network.FriendlyByteBuf;
/** Forge 的整数通道包装；身份追加在全部旧包之后。 */
public final class GroupResponsePacket {
    private final GroupReply value;

    public GroupResponsePacket(GroupReply value) {
        this.value = value;
    }

    public GroupReply value() { return value; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GroupResponsePacket)) return false;
        GroupResponsePacket that = (GroupResponsePacket) other;
        return java.util.Objects.equals(this.value, that.value);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(value);
        return hash;
    }

    @Override public String toString() { return "GroupResponsePacket[value=" + value + "]"; }

    public static void encode(GroupResponsePacket v, FriendlyByteBuf b) {
        GroupWire.writeReply(v.value(), b, ChatMessage::encode);
    }
    public static GroupResponsePacket decode(FriendlyByteBuf b) {
        return new GroupResponsePacket(GroupWire.readReply(b, ChatMessage::decode));
    }
}
