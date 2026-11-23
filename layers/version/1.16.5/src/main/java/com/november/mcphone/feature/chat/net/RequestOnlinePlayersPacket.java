package com.november.mcphone.feature.chat.net;

import net.minecraft.network.FriendlyByteBuf;

/** C2S：打开"加联系人"界面时请求在线玩家列表，无字段；本人由服务端从连接上下文取。 */
public final class RequestOnlinePlayersPacket {
    public RequestOnlinePlayersPacket() {
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequestOnlinePlayersPacket)) return false;
        return true;
    }

    @Override public int hashCode() {
        return 0;
    }

    @Override public String toString() { return "RequestOnlinePlayersPacket[]"; }


    public static void encode(RequestOnlinePlayersPacket msg, FriendlyByteBuf buf) {
    }

    public static RequestOnlinePlayersPacket decode(FriendlyByteBuf buf) {
        return new RequestOnlinePlayersPacket();
    }
}
