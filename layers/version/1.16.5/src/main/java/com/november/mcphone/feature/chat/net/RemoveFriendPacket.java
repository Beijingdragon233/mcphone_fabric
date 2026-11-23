package com.november.mcphone.feature.chat.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** C2S：解除好友，双向。聊天记录不删——那是双方共有的，重新加回好友历史还在。 */
public final class RemoveFriendPacket {
    private final UUID target;

    public RemoveFriendPacket(UUID target) {
        this.target = target;
    }

    public UUID target() { return target; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RemoveFriendPacket)) return false;
        RemoveFriendPacket that = (RemoveFriendPacket) other;
        return java.util.Objects.equals(this.target, that.target);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(target);
        return hash;
    }

    @Override public String toString() { return "RemoveFriendPacket[target=" + target + "]"; }


    public static void encode(RemoveFriendPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.target());
    }

    public static RemoveFriendPacket decode(FriendlyByteBuf buf) {
        return new RemoveFriendPacket(
                buf.readUUID());
    }
}
