package com.november.mcphone.feature.music;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 一首网络歌曲，从 NetMusic 的 CD 上读出来的信息。
 * NetMusic 没有对外 API，它的 SongInfo 只允许出现在 NetMusicCompat 一个文件里，读出来立刻翻译成这个记录；
 * 网络包、服务端逻辑、界面一律只认它，dist 隔离也靠这条。
 *
 * @param url     歌曲地址，可能还要客户端再解析一次
 * @param seconds 时长（秒），0 或负数表示不知道
 */
public final class NetSong {
    private final String url;
    private final String title;
    private final int seconds;

    public NetSong(String url, String title, int seconds) {
        this.url = url;
        this.title = title;
        this.seconds = seconds;
    }

    public String url() { return url; }

    public String title() { return title; }

    public int seconds() { return seconds; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NetSong)) return false;
        NetSong that = (NetSong) other;
        return java.util.Objects.equals(this.url, that.url)
                && java.util.Objects.equals(this.title, that.title)
                && this.seconds == that.seconds;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(url);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(seconds);
        return hash;
    }

    @Override public String toString() { return "NetSong[url=" + url + ", title=" + title + ", seconds=" + seconds + "]"; }


    /** 字符串长度上限，防伪造客户端塞超长字符串 */
    public static final int MAX_TITLE = 256;

    public static final int MAX_URL = 1024;

    public static void encode(NetSong msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.url(), MAX_URL);
        buf.writeUtf(msg.title(), MAX_TITLE);
        buf.writeVarInt(msg.seconds());
    }

    public static NetSong decode(FriendlyByteBuf buf) {
        return new NetSong(
                buf.readUtf(MAX_URL),
                buf.readUtf(MAX_TITLE),
                buf.readVarInt());
    }

    /** 时长换成游戏刻，好与原版唱片的 lengthInTicks 用同一套算术 */
    public long lengthInTicks() {
        return Math.max(0L, (long) seconds * 20L);
    }
}
