package com.november.mcphone.feature.chat.group;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import java.util.UUID;

/** 待本人处理的入群邀请；唯一 ID 约束同意/拒绝，创建时不会取得成员资格。 */
public final class GroupInvitation {
    private final UUID id;
    private final UUID group;
    private final GroupMember inviter;
    private final UUID recipient;
    private final long createdAt;
    private final long expiresAt;

    public UUID id() { return id; }

    public UUID group() { return group; }

    public GroupMember inviter() { return inviter; }

    public UUID recipient() { return recipient; }

    public long createdAt() { return createdAt; }

    public long expiresAt() { return expiresAt; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GroupInvitation)) return false;
        GroupInvitation that = (GroupInvitation) other;
        return java.util.Objects.equals(this.id, that.id)
                && java.util.Objects.equals(this.group, that.group)
                && java.util.Objects.equals(this.inviter, that.inviter)
                && java.util.Objects.equals(this.recipient, that.recipient)
                && this.createdAt == that.createdAt
                && this.expiresAt == that.expiresAt;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(group);
        hash = 31 * hash + java.util.Objects.hashCode(inviter);
        hash = 31 * hash + java.util.Objects.hashCode(recipient);
        hash = 31 * hash + java.util.Objects.hashCode(createdAt);
        hash = 31 * hash + java.util.Objects.hashCode(expiresAt);
        return hash;
    }

    @Override public String toString() { return "GroupInvitation[id=" + id + ", group=" + group + ", inviter=" + inviter + ", recipient=" + recipient + ", createdAt=" + createdAt + ", expiresAt=" + expiresAt + "]"; }

    public GroupInvitation(UUID id, UUID group, GroupMember inviter, UUID recipient, long createdAt, long expiresAt) {
        java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(group);
        java.util.Objects.requireNonNull(inviter); java.util.Objects.requireNonNull(recipient);
        if (inviter.id().equals(recipient) || createdAt < 0 || expiresAt <= createdAt) throw new GroupFault(GroupStatus.INVALID);
        this.id = id;
        this.group = group;
        this.inviter = inviter;
        this.recipient = recipient;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
    public boolean expired(long now) { return now >= expiresAt; }
    public static final Codec<GroupInvitation> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(GroupInvitation::id),
            UUIDUtil.CODEC.fieldOf("group").forGetter(GroupInvitation::group),
            GroupMember.CODEC.fieldOf("inviter").forGetter(GroupInvitation::inviter),
            UUIDUtil.CODEC.fieldOf("recipient").forGetter(GroupInvitation::recipient),
            Codec.LONG.fieldOf("created_at").forGetter(GroupInvitation::createdAt),
            Codec.LONG.fieldOf("expires_at").forGetter(GroupInvitation::expiresAt)).apply(i, GroupInvitation::new));
}
