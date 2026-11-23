package com.november.mcphone.feature.music;

/**
 * 一首曲子，播放器认识的最小单位。只存音源名与音源内编号，怎么变成声音由音源自己决定。
 *
 * @param id         音源自己的编号：本地文件是文件名，网络歌是歌曲 ID
 * @param durationMs 时长（毫秒），-1 表示不知道；本地文件一律 -1（扫目录时不许开文件，时长由流带着走，见 KnownDuration）
 */
public final class Track {
    private final String sourceId;
    private final String id;
    private final String title;
    private final long durationMs;
    private final Kind kind;

    public Track(String sourceId, String id, String title, long durationMs, Kind kind) {
        this.sourceId = sourceId;
        this.id = id;
        this.title = title;
        this.durationMs = durationMs;
        this.kind = kind;
    }

    public String sourceId() { return sourceId; }

    public String id() { return id; }

    public String title() { return title; }

    public long durationMs() { return durationMs; }

    public Kind kind() { return kind; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Track)) return false;
        Track that = (Track) other;
        return java.util.Objects.equals(this.sourceId, that.sourceId)
                && java.util.Objects.equals(this.id, that.id)
                && java.util.Objects.equals(this.title, that.title)
                && this.durationMs == that.durationMs
                && java.util.Objects.equals(this.kind, that.kind);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(durationMs);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        return hash;
    }

    @Override public String toString() { return "Track[sourceId=" + sourceId + ", id=" + id + ", title=" + title + ", durationMs=" + durationMs + ", kind=" + kind + "]"; }


    /** 放法，决定谁听得见 */
    public enum Kind {
        /** 本地解码，只有自己听得见（"耳机"）；能暂停、继续、看进度 */
        LOCAL,

        /** 交给服务端播已注册的音效，周围人都听得见（"外放"）；只能播和停。本地文件不能外放：别人电脑上没有那个文件 */
        SHARED
    }

    /** 全局唯一的键：音源之间的 id 可能撞车，加上音源名才不会 */
    public String key() {
        return sourceId + ":" + id;
    }

    public static final long UNKNOWN_DURATION = -1L;
}
