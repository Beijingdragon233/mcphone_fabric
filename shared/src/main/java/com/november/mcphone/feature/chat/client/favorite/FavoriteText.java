package com.november.mcphone.feature.chat.client.favorite;

import com.november.mcphone.util.Java8;
import com.november.mcphone.feature.chat.TextBody;
import java.util.Objects;
import java.util.UUID;
/** 旧版文字 JSON 的迁移快照；新版持久化使用 FavoriteEntry 与独立正文文件。 */
public final class FavoriteText {
    private final UUID message;
    private final UUID target;
    private final boolean group;
    private final UUID sender;
    private final String senderName;
    private final String sourceName;
    private final String text;
    private final long messageTime;
    private final long savedTime;

    public UUID message() { return message; }

    public UUID target() { return target; }

    public boolean group() { return group; }

    public UUID sender() { return sender; }

    public String senderName() { return senderName; }

    public String sourceName() { return sourceName; }

    public String text() { return text; }

    public long messageTime() { return messageTime; }

    public long savedTime() { return savedTime; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FavoriteText)) return false;
        FavoriteText that = (FavoriteText) other;
        return java.util.Objects.equals(this.message, that.message)
                && java.util.Objects.equals(this.target, that.target)
                && this.group == that.group
                && java.util.Objects.equals(this.sender, that.sender)
                && java.util.Objects.equals(this.senderName, that.senderName)
                && java.util.Objects.equals(this.sourceName, that.sourceName)
                && java.util.Objects.equals(this.text, that.text)
                && this.messageTime == that.messageTime
                && this.savedTime == that.savedTime;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(message);
        hash = 31 * hash + java.util.Objects.hashCode(target);
        hash = 31 * hash + java.util.Objects.hashCode(group);
        hash = 31 * hash + java.util.Objects.hashCode(sender);
        hash = 31 * hash + java.util.Objects.hashCode(senderName);
        hash = 31 * hash + java.util.Objects.hashCode(sourceName);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(messageTime);
        hash = 31 * hash + java.util.Objects.hashCode(savedTime);
        return hash;
    }

    @Override public String toString() { return "FavoriteText[message=" + message + ", target=" + target + ", group=" + group + ", sender=" + sender + ", senderName=" + senderName + ", sourceName=" + sourceName + ", text=" + text + ", messageTime=" + messageTime + ", savedTime=" + savedTime + "]"; }

    public FavoriteText(UUID message, UUID target, boolean group, UUID sender, String senderName, String sourceName, String text, long messageTime, long savedTime) {
        Objects.requireNonNull(message); Objects.requireNonNull(target); Objects.requireNonNull(sender);
        Objects.requireNonNull(senderName); Objects.requireNonNull(sourceName); Objects.requireNonNull(text);
        if (senderName.length()>64 || sourceName.length()>128 || Java8.isBlank(text) || text.length()>TextBody.MAX_LENGTH
                || messageTime<0 || savedTime<0) throw new IllegalArgumentException("无效文字收藏");
        this.message = message;
        this.target = target;
        this.group = group;
        this.sender = sender;
        this.senderName = senderName;
        this.sourceName = sourceName;
        this.text = text;
        this.messageTime = messageTime;
        this.savedTime = savedTime;
    }
    public String key() { return (group?"group:":"direct:")+target+":"+message; }
}
