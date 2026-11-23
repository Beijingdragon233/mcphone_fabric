package com.november.mcphone.feature.notes.net;

import net.minecraft.network.FriendlyByteBuf;

/** 网络包：客户端 → 服务端，请求某一条笔记的全文（列表只带摘要，点进去才拉） */
public final class RequestNotePacket {
    private final int id;

    public RequestNotePacket(int id) {
        this.id = id;
    }

    public int id() { return id; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequestNotePacket)) return false;
        RequestNotePacket that = (RequestNotePacket) other;
        return this.id == that.id;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        return hash;
    }

    @Override public String toString() { return "RequestNotePacket[id=" + id + "]"; }


    public static void encode(RequestNotePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.id());
    }

    public static RequestNotePacket decode(FriendlyByteBuf buf) {
        return new RequestNotePacket(
                buf.readVarInt());
    }
}
