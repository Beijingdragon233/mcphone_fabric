package com.november.mcphone.feature.chat.group;

import com.november.mcphone.util.Java8;

import java.util.UUID;

/** 仅发给被邀请者的通知投影，不携带成员清单、消息或图片身份。群名取当前服务器资料。 */
public final class GroupInviteNotice {
    private final UUID id;
    private final UUID group;
    private final String name;
    private final GroupMember inviter;
    private final long createdAt;
    private final long expiresAt;

    public UUID id() { return id; }

    public UUID group() { return group; }

    public String name() { return name; }

    public GroupMember inviter() { return inviter; }

    public long createdAt() { return createdAt; }

    public long expiresAt() { return expiresAt; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GroupInviteNotice)) return false;
        GroupInviteNotice that = (GroupInviteNotice) other;
        return java.util.Objects.equals(this.id, that.id)
                && java.util.Objects.equals(this.group, that.group)
                && java.util.Objects.equals(this.name, that.name)
                && java.util.Objects.equals(this.inviter, that.inviter)
                && this.createdAt == that.createdAt
                && this.expiresAt == that.expiresAt;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(group);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(inviter);
        hash = 31 * hash + java.util.Objects.hashCode(createdAt);
        hash = 31 * hash + java.util.Objects.hashCode(expiresAt);
        return hash;
    }

    @Override public String toString() { return "GroupInviteNotice[id=" + id + ", group=" + group + ", name=" + name + ", inviter=" + inviter + ", createdAt=" + createdAt + ", expiresAt=" + expiresAt + "]"; }

    public GroupInviteNotice(UUID id, UUID group, String name, GroupMember inviter, long createdAt, long expiresAt) {
        java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(group); java.util.Objects.requireNonNull(inviter);
        if (name == null || Java8.isBlank(name) || name.length() > GroupLimits.NAME_LENGTH || createdAt < 0 || expiresAt <= createdAt)
            throw new GroupFault(GroupStatus.INVALID);
        this.id = id;
        this.group = group;
        this.name = name;
        this.inviter = inviter;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
    static GroupInviteNotice of(GroupInvitation invitation, GroupRoom room) {
        return new GroupInviteNotice(invitation.id(), room.id(), room.name(), invitation.inviter(), invitation.createdAt(), invitation.expiresAt());
    }
}
