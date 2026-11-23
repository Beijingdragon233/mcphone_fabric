package com.november.mcphone.feature.chat.group;

import com.november.mcphone.util.Java8;

import com.google.common.collect.ImmutableList;
import com.november.mcphone.util.Java8Collections;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 只给本群成员的个人摘要，最后一条与未读数已应用本人的删除记录。 */
public final class GroupSummary {
    private final UUID id;
    private final String name;
    private final UUID owner;
    private final int memberCount;
    private final List<UUID> portraits;
    private final Optional<GroupMessage> last;
    private final int unread;

    public UUID id() { return id; }

    public String name() { return name; }

    public UUID owner() { return owner; }

    public int memberCount() { return memberCount; }

    public List<UUID> portraits() { return portraits; }

    public Optional<GroupMessage> last() { return last; }

    public int unread() { return unread; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GroupSummary)) return false;
        GroupSummary that = (GroupSummary) other;
        return java.util.Objects.equals(this.id, that.id)
                && java.util.Objects.equals(this.name, that.name)
                && java.util.Objects.equals(this.owner, that.owner)
                && this.memberCount == that.memberCount
                && java.util.Objects.equals(this.portraits, that.portraits)
                && java.util.Objects.equals(this.last, that.last)
                && this.unread == that.unread;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(owner);
        hash = 31 * hash + java.util.Objects.hashCode(memberCount);
        hash = 31 * hash + java.util.Objects.hashCode(portraits);
        hash = 31 * hash + java.util.Objects.hashCode(last);
        hash = 31 * hash + java.util.Objects.hashCode(unread);
        return hash;
    }

    @Override public String toString() { return "GroupSummary[id=" + id + ", name=" + name + ", owner=" + owner + ", memberCount=" + memberCount + ", portraits=" + portraits + ", last=" + last + ", unread=" + unread + "]"; }

    public GroupSummary(UUID id, String name, UUID owner, int memberCount, List<UUID> portraits, Optional<GroupMessage> last, int unread) {
        java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(owner);
        portraits = ImmutableList.copyOf(portraits); java.util.Objects.requireNonNull(last);
        if (name == null || Java8.isBlank(name) || name.length() > GroupLimits.NAME_LENGTH || memberCount < 1
                || memberCount > GroupLimits.MEMBERS || portraits.size() > 4 || unread < 0 || unread > GroupLimits.MESSAGES)
            throw new GroupFault(GroupStatus.INVALID);
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.memberCount = memberCount;
        this.portraits = portraits;
        this.last = last;
        this.unread = unread;
    }
    public static GroupSummary of(GroupRoom room, UUID player) {
        List<GroupMessage> visible = room.visible(player);
        return new GroupSummary(room.id(), room.name(), room.owner(), room.members().size(),
                room.members().stream().limit(4).map(GroupMember::id).collect(Java8Collections.toList()),
                visible.isEmpty() ? Optional.empty() : Optional.of(visible.get(visible.size() - 1)), room.unread(player));
    }
    public long lastTime() { return last.map(m -> m.message().time()).orElse(0L); }
}
