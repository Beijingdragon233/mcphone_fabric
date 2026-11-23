package com.november.mcphone.feature.chat.net;

import com.november.mcphone.core.net.Wire;
import net.minecraft.network.FriendlyByteBuf;
import com.november.mcphone.feature.chat.FriendData;

import java.util.List;

/** S2C：整个会话列表，只含摘要。上限取好友数上限并写在编解码器上，解码阶段就拒收超量数据。 */
public final class SyncConversationsPacket {
    private final List<ConversationSummary> conversations;

    public SyncConversationsPacket(List<ConversationSummary> conversations) {
        this.conversations = conversations;
    }

    public List<ConversationSummary> conversations() { return conversations; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SyncConversationsPacket)) return false;
        SyncConversationsPacket that = (SyncConversationsPacket) other;
        return java.util.Objects.equals(this.conversations, that.conversations);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(conversations);
        return hash;
    }

    @Override public String toString() { return "SyncConversationsPacket[conversations=" + conversations + "]"; }


    public static final int MAX_CONVERSATIONS = FriendData.MAX_FRIENDS;

    public static void encode(SyncConversationsPacket msg, FriendlyByteBuf buf) {
        Wire.writeList(buf, msg.conversations(), MAX_CONVERSATIONS, ConversationSummary::encode);
    }

    public static SyncConversationsPacket decode(FriendlyByteBuf buf) {
        return new SyncConversationsPacket(
                Wire.readList(buf, MAX_CONVERSATIONS, ConversationSummary::decode));
    }
}
