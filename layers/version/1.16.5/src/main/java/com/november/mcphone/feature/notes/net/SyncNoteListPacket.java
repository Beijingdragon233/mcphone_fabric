package com.november.mcphone.feature.notes.net;

import com.november.mcphone.core.net.Wire;
import net.minecraft.network.FriendlyByteBuf;
import com.november.mcphone.feature.notes.NoteList;
import com.november.mcphone.feature.notes.NoteSummary;

import java.util.List;

/** 网络包：服务端 → 客户端，笔记列表（只有摘要，没有全文） */
public final class SyncNoteListPacket {
    private final List<NoteSummary> notes;

    public SyncNoteListPacket(List<NoteSummary> notes) {
        this.notes = notes;
    }

    public List<NoteSummary> notes() { return notes; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SyncNoteListPacket)) return false;
        SyncNoteListPacket that = (SyncNoteListPacket) other;
        return java.util.Objects.equals(this.notes, that.notes);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(notes);
        return hash;
    }

    @Override public String toString() { return "SyncNoteListPacket[notes=" + notes + "]"; }


    public static void encode(SyncNoteListPacket msg, FriendlyByteBuf buf) {
        Wire.writeList(buf, msg.notes(), NoteList.MAX_COUNT, NoteSummary::encode);
    }

    public static SyncNoteListPacket decode(FriendlyByteBuf buf) {
        return new SyncNoteListPacket(
                Wire.readList(buf, NoteList.MAX_COUNT, NoteSummary::decode));
    }
}
