package com.november.mcphone.feature.notes.net;

import net.minecraft.network.FriendlyByteBuf;
import com.november.mcphone.feature.notes.Note;

/**
 * 网络包：客户端 → 服务端，保存一条笔记。id 为 NoteService.NEW_NOTE_ID 表示新建，
 * 真正的 id 由服务端分配；不带时间戳，客户端的钟不可信。
 */
public final class SaveNotePacket {
    private final int id;
    private final String body;

    public SaveNotePacket(int id, String body) {
        this.id = id;
        this.body = body;
    }

    public int id() { return id; }

    public String body() { return body; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SaveNotePacket)) return false;
        SaveNotePacket that = (SaveNotePacket) other;
        return this.id == that.id
                && java.util.Objects.equals(this.body, that.body);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(body);
        return hash;
    }

    @Override public String toString() { return "SaveNotePacket[id=" + id + ", body=" + body + "]"; }


    public static void encode(SaveNotePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.id());
        buf.writeUtf(msg.body(), Note.MAX_BODY_LENGTH);
    }

    public static SaveNotePacket decode(FriendlyByteBuf buf) {
        return new SaveNotePacket(
                buf.readVarInt(),
                buf.readUtf(Note.MAX_BODY_LENGTH));
    }
}
