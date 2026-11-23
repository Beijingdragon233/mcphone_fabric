package com.november.mcphone.api.sdk.notify;

import com.google.common.collect.ImmutableList;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 一条通知（施工方案 §23.3，形状按 §33.2 的扩展版）。角标的唯一来源 ——
 * 各 App 自己记一份的话，首页汇不出总数。
 *
 * <h2>为什么不照 §23.3 那个七字段的原型</h2>
 *
 * §33.2 标题下第一行就写着"扩展 §23.3"，把它扩成了带 {@code bodyKey} / {@code dedupeKey} /
 * {@code expiresAt} 的形状，而 §33.4 有一条判据是"同一 {@code dedupeKey} 连发 5 条 → 玩家只看到 1 条"。
 * A 档一冻结就改不动（§23.5），按七字段的原型冻下去，那条判据<b>永远实现不了</b>。
 *
 * <p>另外补一个 {@link #id}：没有它就没法标记单条已读、没法在客户端缓存里定位一条通知。
 * {@code read} 不在这里 —— 它是每玩家的可变状态，塞进值类型意味着每一份存下来的通知都在撒谎
 * （与 {@code PlayerRef.online} 同一个错）。未读与否问 {@link INotifications#unread}。
 *
 * <p><b>{@link #titleKey} / {@link #bodyKey} 是本地化键，不是文本</b>（与 §15.3 同一个理由）：
 * 收文本就等于让后端往客户端推任意字符串，那是一条钓鱼与刷屏的路。
 *
 * @param id        这一条的标识，同一个 App 内唯一
 * @param appId     哪个 App 发的，由宿主填
 * @param topic     同一个 App 内的分类，用于折叠
 * @param titleKey  标题的本地化键
 * @param titleArgs 标题参数，可空
 * @param bodyKey   正文的本地化键，可为 null
 * @param bodyArgs  正文参数，可空
 * @param createdAt 毫秒时间戳。<b>脚本侧读到的是十进制字符串</b>——毫秒数会超过 2^53（§23.3）
 * @param priority  轻重
 * @param dedupeKey 去重键，可为 null。同一个键的连发只留最新一条（§33.4）
 * @param expiresAt 过期时刻的毫秒时间戳，0 表示不过期
 */
public final class Notification {
    private final long id;
    private final ResourceLocation appId;
    private final String topic;
    private final String titleKey;
    private final List<String> titleArgs;
    private final String bodyKey;
    private final List<String> bodyArgs;
    private final long createdAt;
    private final Priority priority;
    private final String dedupeKey;
    private final long expiresAt;

    public long id() { return id; }

    public ResourceLocation appId() { return appId; }

    public String topic() { return topic; }

    public String titleKey() { return titleKey; }

    public List<String> titleArgs() { return titleArgs; }

    public String bodyKey() { return bodyKey; }

    public List<String> bodyArgs() { return bodyArgs; }

    public long createdAt() { return createdAt; }

    public Priority priority() { return priority; }

    public String dedupeKey() { return dedupeKey; }

    public long expiresAt() { return expiresAt; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Notification)) return false;
        Notification that = (Notification) other;
        return this.id == that.id
                && java.util.Objects.equals(this.appId, that.appId)
                && java.util.Objects.equals(this.topic, that.topic)
                && java.util.Objects.equals(this.titleKey, that.titleKey)
                && java.util.Objects.equals(this.titleArgs, that.titleArgs)
                && java.util.Objects.equals(this.bodyKey, that.bodyKey)
                && java.util.Objects.equals(this.bodyArgs, that.bodyArgs)
                && this.createdAt == that.createdAt
                && java.util.Objects.equals(this.priority, that.priority)
                && java.util.Objects.equals(this.dedupeKey, that.dedupeKey)
                && this.expiresAt == that.expiresAt;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(appId);
        hash = 31 * hash + java.util.Objects.hashCode(topic);
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(titleArgs);
        hash = 31 * hash + java.util.Objects.hashCode(bodyKey);
        hash = 31 * hash + java.util.Objects.hashCode(bodyArgs);
        hash = 31 * hash + java.util.Objects.hashCode(createdAt);
        hash = 31 * hash + java.util.Objects.hashCode(priority);
        hash = 31 * hash + java.util.Objects.hashCode(dedupeKey);
        hash = 31 * hash + java.util.Objects.hashCode(expiresAt);
        return hash;
    }

    @Override public String toString() { return "Notification[id=" + id + ", appId=" + appId + ", topic=" + topic + ", titleKey=" + titleKey + ", titleArgs=" + titleArgs + ", bodyKey=" + bodyKey + ", bodyArgs=" + bodyArgs + ", createdAt=" + createdAt + ", priority=" + priority + ", dedupeKey=" + dedupeKey + ", expiresAt=" + expiresAt + "]"; }


    /** 每 App 每玩家的条数上限，超出淘汰最旧的<b>已读</b>项（§23.3）。 */
    public static final int MAX_PER_APP = 32;

    /** {@link #topic} 与 {@link #dedupeKey} 的长度上限。 */
    public static final int MAX_KEY = 64;

    public Notification(long id, ResourceLocation appId, String topic, String titleKey, List<String> titleArgs, String bodyKey, List<String> bodyArgs, long createdAt, Priority priority, String dedupeKey, long expiresAt) {
        if (appId == null) throw new IllegalArgumentException("Notification.appId 不能为 null");
        if (titleKey == null || titleKey.isEmpty()) {
            throw new IllegalArgumentException("titleKey 是本地化键，不能为空");
        }
        topic = clamp(topic, "topic");
        dedupeKey = dedupeKey == null ? null : clamp(dedupeKey, "dedupeKey");
        titleArgs = titleArgs == null ? ImmutableList.of() : ImmutableList.copyOf(titleArgs);
        bodyArgs = bodyArgs == null ? ImmutableList.of() : ImmutableList.copyOf(bodyArgs);
        if (priority == null) priority = Priority.NORMAL;
        if (expiresAt < 0) throw new IllegalArgumentException("expiresAt 不能为负");
        this.id = id;
        this.appId = appId;
        this.topic = topic;
        this.titleKey = titleKey;
        this.titleArgs = titleArgs;
        this.bodyKey = bodyKey;
        this.bodyArgs = bodyArgs;
        this.createdAt = createdAt;
        this.priority = priority;
        this.dedupeKey = dedupeKey;
        this.expiresAt = expiresAt;
    }

    private static String clamp(String s, String field) {
        if (s == null) return "";
        if (s.length() > MAX_KEY) {
            throw new IllegalArgumentException("Notification." + field + " 最长 " + MAX_KEY + "，收到 " + s.length());
        }
        return s;
    }

    /** 过没过期。{@link #expiresAt} 为 0 时永不过期。 */
    public boolean expired(long now) {
        return expiresAt > 0 && now >= expiresAt;
    }
}
