package com.november.mcphone.feature.chat.net;

import com.november.mcphone.core.net.Wire;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/** S2C：在线玩家列表（已排除本人），截断到 MAX_PLAYERS；totalOnline 是真实总数，界面据此写出"显示前 N 人 / 共 M 人"。 */
public final class SyncOnlinePlayersPacket {
    private final List<OnlinePlayer> players;
    private final int totalOnline;

    public SyncOnlinePlayersPacket(List<OnlinePlayer> players, int totalOnline) {
        this.players = players;
        this.totalOnline = totalOnline;
    }

    public List<OnlinePlayer> players() { return players; }

    public int totalOnline() { return totalOnline; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SyncOnlinePlayersPacket)) return false;
        SyncOnlinePlayersPacket that = (SyncOnlinePlayersPacket) other;
        return java.util.Objects.equals(this.players, that.players)
                && this.totalOnline == that.totalOnline;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(players);
        hash = 31 * hash + java.util.Objects.hashCode(totalOnline);
        return hash;
    }

    @Override public String toString() { return "SyncOnlinePlayersPacket[players=" + players + ", totalOnline=" + totalOnline + "]"; }


    public static final int MAX_PLAYERS = 200;

    public static void encode(SyncOnlinePlayersPacket msg, FriendlyByteBuf buf) {
        Wire.writeList(buf, msg.players(), MAX_PLAYERS, OnlinePlayer::encode);
        buf.writeVarInt(msg.totalOnline());
    }

    public static SyncOnlinePlayersPacket decode(FriendlyByteBuf buf) {
        return new SyncOnlinePlayersPacket(
                Wire.readList(buf, MAX_PLAYERS, OnlinePlayer::decode),
                buf.readVarInt());
    }

}
