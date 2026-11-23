package com.november.mcphone.feature.chat.client.favorite;

import com.november.mcphone.util.Java8;

import com.november.mcphone.feature.chat.ChatImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.UUID;

/** 索引只存元信息；正文位于同一环境的 text/、image/，文件名由身份计算。 */
public final class FavoriteEntry {
    private final UUID message;
    private final UUID target;
    private final boolean group;
    private final UUID sender;
    private final String senderName;
    private final long messageTime;
    private final long savedTime;
    private final Kind kind;
    private final long bytes;
    private final int width;
    private final int height;
    private final int frames;
    private final int frameMs;

    public UUID message() { return message; }

    public UUID target() { return target; }

    public boolean group() { return group; }

    public UUID sender() { return sender; }

    public String senderName() { return senderName; }

    public long messageTime() { return messageTime; }

    public long savedTime() { return savedTime; }

    public Kind kind() { return kind; }

    public long bytes() { return bytes; }

    public int width() { return width; }

    public int height() { return height; }

    public int frames() { return frames; }

    public int frameMs() { return frameMs; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FavoriteEntry)) return false;
        FavoriteEntry that = (FavoriteEntry) other;
        return java.util.Objects.equals(this.message, that.message)
                && java.util.Objects.equals(this.target, that.target)
                && this.group == that.group
                && java.util.Objects.equals(this.sender, that.sender)
                && java.util.Objects.equals(this.senderName, that.senderName)
                && this.messageTime == that.messageTime
                && this.savedTime == that.savedTime
                && java.util.Objects.equals(this.kind, that.kind)
                && this.bytes == that.bytes
                && this.width == that.width
                && this.height == that.height
                && this.frames == that.frames
                && this.frameMs == that.frameMs;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(message);
        hash = 31 * hash + java.util.Objects.hashCode(target);
        hash = 31 * hash + java.util.Objects.hashCode(group);
        hash = 31 * hash + java.util.Objects.hashCode(sender);
        hash = 31 * hash + java.util.Objects.hashCode(senderName);
        hash = 31 * hash + java.util.Objects.hashCode(messageTime);
        hash = 31 * hash + java.util.Objects.hashCode(savedTime);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(bytes);
        hash = 31 * hash + java.util.Objects.hashCode(width);
        hash = 31 * hash + java.util.Objects.hashCode(height);
        hash = 31 * hash + java.util.Objects.hashCode(frames);
        hash = 31 * hash + java.util.Objects.hashCode(frameMs);
        return hash;
    }

    @Override public String toString() { return "FavoriteEntry[message=" + message + ", target=" + target + ", group=" + group + ", sender=" + sender + ", senderName=" + senderName + ", messageTime=" + messageTime + ", savedTime=" + savedTime + ", kind=" + kind + ", bytes=" + bytes + ", width=" + width + ", height=" + height + ", frames=" + frames + ", frameMs=" + frameMs + "]"; }

    public enum Kind {
        TEXT("text", ".txt"), IMAGE("image", ".png");
        public final String folder, extension;
        Kind(String folder, String extension) { this.folder = folder; this.extension = extension; }
    }
    public FavoriteEntry(UUID message, UUID target, boolean group, UUID sender, String senderName, long messageTime, long savedTime, Kind kind, long bytes, int width, int height, int frames, int frameMs) {
        Objects.requireNonNull(message); Objects.requireNonNull(target); Objects.requireNonNull(sender);
        Objects.requireNonNull(senderName); Objects.requireNonNull(kind);
        if (senderName.length() > 64 || messageTime < 0 || savedTime < 0 || bytes <= 0)
            throw new IllegalArgumentException("无效收藏元信息");
        if (kind == Kind.TEXT) {
            if (bytes > com.november.mcphone.feature.chat.TextBody.MAX_LENGTH * 4L
                    || width != 0 || height != 0 || frames != 1 || frameMs != 0)
                throw new IllegalArgumentException("无效文字收藏");
        } else if (bytes > ChatImage.MAX_BYTES_CEILING || width < 1 || height < 1
                || width > ChatImage.MAX_SIDE || height > ChatImage.MAX_SIDE
                || frames < 1 || frames > ChatImage.MAX_FRAMES
                || frameMs < 0 || frames > 1 && frameMs < 1 || frames == 1 && frameMs != 0)
            throw new IllegalArgumentException("无效图片收藏");
        this.message = message;
        this.target = target;
        this.group = group;
        this.sender = sender;
        this.senderName = senderName;
        this.messageTime = messageTime;
        this.savedTime = savedTime;
        this.kind = kind;
        this.bytes = bytes;
        this.width = width;
        this.height = height;
        this.frames = frames;
        this.frameMs = frameMs;
    }
    public String key() { return (group ? "group:" : "direct:") + target + ":" + message; }
    public String fileName() {
        try {
            return Java8.formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key().getBytes(StandardCharsets.UTF_8))) + kind.extension;
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static FavoriteEntry text(FavoriteText old) {
        return new FavoriteEntry(old.message(), old.target(), old.group(), old.sender(), old.senderName(),
                old.messageTime(), old.savedTime(), Kind.TEXT, old.text().getBytes(StandardCharsets.UTF_8).length, 0, 0, 1, 0);
    }
}
