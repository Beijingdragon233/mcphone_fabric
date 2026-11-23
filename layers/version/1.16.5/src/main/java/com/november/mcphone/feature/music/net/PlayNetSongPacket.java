package com.november.mcphone.feature.music.net;

import net.minecraft.network.FriendlyByteBuf;
import com.november.mcphone.feature.music.NetSong;

/**
 * S2C：某个人的手机开始外放一首网络歌。发地址不发音频，每个客户端自己去拉。
 * 带实体 ID 而不是坐标：声音要跟着人走，与原版唱片那一支（绑在实体上）一致。
 */
public final class PlayNetSongPacket {
    private final int entityId;
    private final NetSong song;

    public PlayNetSongPacket(int entityId, NetSong song) {
        this.entityId = entityId;
        this.song = song;
    }

    public int entityId() { return entityId; }

    public NetSong song() { return song; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PlayNetSongPacket)) return false;
        PlayNetSongPacket that = (PlayNetSongPacket) other;
        return this.entityId == that.entityId
                && java.util.Objects.equals(this.song, that.song);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(entityId);
        hash = 31 * hash + java.util.Objects.hashCode(song);
        return hash;
    }

    @Override public String toString() { return "PlayNetSongPacket[entityId=" + entityId + ", song=" + song + "]"; }


    public static void encode(PlayNetSongPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId());
        NetSong.encode(msg.song(), buf);
    }

    public static PlayNetSongPacket decode(FriendlyByteBuf buf) {
        return new PlayNetSongPacket(
                buf.readVarInt(),
                NetSong.decode(buf));
    }
}
