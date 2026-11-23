package com.november.mcphone.feature.chat.group.net;
import com.november.mcphone.feature.chat.ChatMessage;
import net.minecraft.network.FriendlyByteBuf;
/** Forge 的整数通道包装；身份追加在全部旧包之后。 */
public final class GroupRequestPacket {
    private final GroupCommand value;

    public GroupRequestPacket(GroupCommand value) {
        this.value = value;
    }

    public GroupCommand value() { return value; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GroupRequestPacket)) return false;
        GroupRequestPacket that = (GroupRequestPacket) other;
        return java.util.Objects.equals(this.value, that.value);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(value);
        return hash;
    }

    @Override public String toString() { return "GroupRequestPacket[value=" + value + "]"; }

    public static void encode(GroupRequestPacket v, FriendlyByteBuf b) {
        GroupWire.writeCommand(v.value(), b);
    }
    public static GroupRequestPacket decode(FriendlyByteBuf b) {
        return new GroupRequestPacket(GroupWire.readCommand(b));
    }
}
