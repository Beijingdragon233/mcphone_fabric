package com.november.mcphone.feature.chat.group;

import com.november.mcphone.util.Java8;

import com.google.common.collect.ImmutableList;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** 成员详情单独请求，不把整张成员表塞进每一帧的会话摘要。 */
public final class GroupInfo {
    private final UUID id;
    private final String name;
    private final UUID owner;
    private final List<GroupMember> members;

    public UUID id() { return id; }

    public String name() { return name; }

    public UUID owner() { return owner; }

    public List<GroupMember> members() { return members; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GroupInfo)) return false;
        GroupInfo that = (GroupInfo) other;
        return java.util.Objects.equals(this.id, that.id)
                && java.util.Objects.equals(this.name, that.name)
                && java.util.Objects.equals(this.owner, that.owner)
                && java.util.Objects.equals(this.members, that.members);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(owner);
        hash = 31 * hash + java.util.Objects.hashCode(members);
        return hash;
    }

    @Override public String toString() { return "GroupInfo[id=" + id + ", name=" + name + ", owner=" + owner + ", members=" + members + "]"; }

    public GroupInfo(UUID id, String name, UUID owner, List<GroupMember> members) {
        java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(owner); members = ImmutableList.copyOf(members);
        if (name == null || Java8.isBlank(name) || name.length() > GroupLimits.NAME_LENGTH
                || members.isEmpty() || members.size() > GroupLimits.MEMBERS) throw new GroupFault(GroupStatus.INVALID);
        HashSet<UUID> unique = new HashSet<UUID>();
        for (GroupMember member : members) if (!unique.add(member.id())) throw new GroupFault(GroupStatus.INVALID);
        if (!unique.contains(owner)) throw new GroupFault(GroupStatus.INVALID);
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.members = members;
    }
    public static GroupInfo of(GroupRoom room) { return new GroupInfo(room.id(), room.name(), room.owner(), room.members()); }
}
