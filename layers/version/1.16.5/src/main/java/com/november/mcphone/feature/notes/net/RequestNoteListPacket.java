package com.november.mcphone.feature.notes.net;

import net.minecraft.network.FriendlyByteBuf;

/** 网络包：客户端 → 服务端，请求笔记列表。不带字段：玩家取自连接上下文，不许指定别人 */
public final class RequestNoteListPacket {
    public RequestNoteListPacket() {
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequestNoteListPacket)) return false;
        return true;
    }

    @Override public int hashCode() {
        return 0;
    }

    @Override public String toString() { return "RequestNoteListPacket[]"; }


    public static void encode(RequestNoteListPacket msg, FriendlyByteBuf buf) {
    }

    public static RequestNoteListPacket decode(FriendlyByteBuf buf) {
        return new RequestNoteListPacket();
    }
}
