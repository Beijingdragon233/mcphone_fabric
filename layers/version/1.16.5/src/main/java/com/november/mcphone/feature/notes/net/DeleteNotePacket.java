package com.november.mcphone.feature.notes.net;

import net.minecraft.network.FriendlyByteBuf;

/** 网络包：客户端 → 服务端，删掉一条笔记 */
public final class DeleteNotePacket {
    private final int id;

    public DeleteNotePacket(int id) {
        this.id = id;
    }

    public int id() { return id; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DeleteNotePacket)) return false;
        DeleteNotePacket that = (DeleteNotePacket) other;
        return this.id == that.id;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        return hash;
    }

    @Override public String toString() { return "DeleteNotePacket[id=" + id + "]"; }


    public static void encode(DeleteNotePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.id());
    }

    public static DeleteNotePacket decode(FriendlyByteBuf buf) {
        return new DeleteNotePacket(
                buf.readVarInt());
    }
}
