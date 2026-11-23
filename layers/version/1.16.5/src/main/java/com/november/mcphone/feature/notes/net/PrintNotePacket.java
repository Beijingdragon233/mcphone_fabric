package com.november.mcphone.feature.notes.net;

import net.minecraft.network.FriendlyByteBuf;

/** 网络包：客户端 → 服务端，把某条笔记印成书。只带 id：采信客户端送来的正文会被伪造 */
public final class PrintNotePacket {
    private final int id;

    public PrintNotePacket(int id) {
        this.id = id;
    }

    public int id() { return id; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PrintNotePacket)) return false;
        PrintNotePacket that = (PrintNotePacket) other;
        return this.id == that.id;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        return hash;
    }

    @Override public String toString() { return "PrintNotePacket[id=" + id + "]"; }


    public static void encode(PrintNotePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.id());
    }

    public static PrintNotePacket decode(FriendlyByteBuf buf) {
        return new PrintNotePacket(
                buf.readVarInt());
    }
}
