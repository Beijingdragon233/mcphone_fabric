package com.november.mcphone.feature.chat.net;

import net.minecraft.network.FriendlyByteBuf;

/** C2S：打开聊天 App 时请求会话列表。故意无字段：玩家取自连接上下文，带玩家 ID 等于给伪造客户端开窥探别人会话的后门。 */
public final class RequestConversationsPacket {
    public RequestConversationsPacket() {
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequestConversationsPacket)) return false;
        return true;
    }

    @Override public int hashCode() {
        return 0;
    }

    @Override public String toString() { return "RequestConversationsPacket[]"; }


    public static void encode(RequestConversationsPacket msg, FriendlyByteBuf buf) {
    }

    public static RequestConversationsPacket decode(FriendlyByteBuf buf) {
        return new RequestConversationsPacket();
    }
}
