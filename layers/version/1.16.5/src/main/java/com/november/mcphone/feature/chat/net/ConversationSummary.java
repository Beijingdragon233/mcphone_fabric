package com.november.mcphone.feature.chat.net;

import com.november.mcphone.feature.chat.MessageBody;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Optional;
import java.util.UUID;

/**
 * 会话列表里的一行摘要；历史消息等玩家点进会话再单独拉。
 * 编解码手写而不用固定字段组合：1.21.1 那边 composite 最多 6 个字段，本记录正好 6 个，用它以后加字段就得推倒重写。
 * online 是瞬时状态，现算现发不落盘；last 为空、lastTime 0 表示还没聊过。
 *
 * 为什么带的是整条正文（{@link MessageBody}）而不是一行预览文字
 *
 * 那一行显示成什么，取决于最后一条是什么消息：文本就是正文，图片是「[图片]」，
 * 日后再多一种就再多一种说法。带正文过来，客户端问一句 {@link MessageBody#preview()} 就有了，
 * 加消息种类时这里一个字都不用改；带一行现成的字过来，则等于让服务端替客户端决定
 * 用哪种语言——服主的服务端是英文的，玩家的客户端是中文的，这种事天天发生。
 */
public final class ConversationSummary {
    private final UUID id;
    private final String name;
    private final boolean online;
    private final Optional<MessageBody> last;
    private final long lastTime;
    private final int unread;

    public ConversationSummary(UUID id, String name, boolean online, Optional<MessageBody> last, long lastTime, int unread) {
        this.id = id;
        this.name = name;
        this.online = online;
        this.last = last;
        this.lastTime = lastTime;
        this.unread = unread;
    }

    public UUID id() { return id; }

    public String name() { return name; }

    public boolean online() { return online; }

    public Optional<MessageBody> last() { return last; }

    public long lastTime() { return lastTime; }

    public int unread() { return unread; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ConversationSummary)) return false;
        ConversationSummary that = (ConversationSummary) other;
        return java.util.Objects.equals(this.id, that.id)
                && java.util.Objects.equals(this.name, that.name)
                && this.online == that.online
                && java.util.Objects.equals(this.last, that.last)
                && this.lastTime == that.lastTime
                && this.unread == that.unread;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(online);
        hash = 31 * hash + java.util.Objects.hashCode(last);
        hash = 31 * hash + java.util.Objects.hashCode(lastTime);
        hash = 31 * hash + java.util.Objects.hashCode(unread);
        return hash;
    }

    @Override public String toString() { return "ConversationSummary[id=" + id + ", name=" + name + ", online=" + online + ", last=" + last + ", lastTime=" + lastTime + ", unread=" + unread + "]"; }


    /**
     * 玩家名长度上限。原版名最长 16，但这是编解码器的硬上限，超了 writeUtf 直接抛异常断线，
     * 而 Geyser 前缀、离线模式、代理都可能给出超过 16 的名字，所以放宽到 32 并由 {@link #clampName} 兜底。
     */
    public static final int MAX_NAME_LENGTH = 32;

    /** 所有下发给客户端的名字都要过这一道；按字符数截，32 字符最多 96 字节，在 writeUtf 的字节上限内 */
    public static String clampName(String name) {
        if (name == null || name.isEmpty()) return "";
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }

    public static void encode(ConversationSummary value, FriendlyByteBuf buf) {
        buf.writeUUID(value.id());
        buf.writeUtf(value.name(), MAX_NAME_LENGTH);
        buf.writeBoolean(value.online());
        // writeOptional 的 Writer 是 (buf, 值)，本仓的 encode 一律是 (值, buf)，所以这里不能直接用方法引用
        buf.writeBoolean(value.last().isPresent());
        value.last().ifPresent(body -> MessageBody.encode(body, buf));
        buf.writeVarLong(value.lastTime());
        buf.writeVarInt(value.unread());
    }

    public static ConversationSummary decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(MAX_NAME_LENGTH);
        boolean online = buf.readBoolean();
        Optional<MessageBody> last = buf.readBoolean() ? Optional.of(MessageBody.decode(buf)) : Optional.empty();
        long lastTime = buf.readVarLong();
        int unread = buf.readVarInt();
        return new ConversationSummary(id, name, online, last, lastTime, unread);
    }

    /** 还没聊过的联系人 */
    public static ConversationSummary empty(UUID id, String name, boolean online) {
        return new ConversationSummary(id, name, online, Optional.empty(), 0L, 0);
    }
}
