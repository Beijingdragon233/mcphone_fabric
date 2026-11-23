package com.november.mcphone.feature.notes.net;

import net.minecraft.network.FriendlyByteBuf;
import com.november.mcphone.feature.notes.Note;

/** 网络包：服务端 → 客户端，某条笔记的全文；已被删时 id 照带但正文为空，由界面退回列表 */
public final class SyncNotePacket {
    private final Note note;

    public SyncNotePacket(Note note) {
        this.note = note;
    }

    public Note note() { return note; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SyncNotePacket)) return false;
        SyncNotePacket that = (SyncNotePacket) other;
        return java.util.Objects.equals(this.note, that.note);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(note);
        return hash;
    }

    @Override public String toString() { return "SyncNotePacket[note=" + note + "]"; }


    public static void encode(SyncNotePacket msg, FriendlyByteBuf buf) {
        Note.encode(msg.note(), buf);
    }

    public static SyncNotePacket decode(FriendlyByteBuf buf) {
        return new SyncNotePacket(
                Note.decode(buf));
    }
}
