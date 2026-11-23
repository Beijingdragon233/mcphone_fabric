package com.november.mcphone.feature.chat.group;

import com.november.mcphone.util.Java8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.november.mcphone.util.Java8Collections;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 完整且不可变的群状态；群 ID 独立于玩家 UUID，不借用双人 ConversationKey。 */
public final class GroupRoom {
    private final UUID id;
    private final String name;
    private final UUID owner;
    private final List<GroupMember> members;
    private final List<GroupMessage> messages;
    private final long sequence;
    private final long revision;
    private final Map<UUID, Long> read;
    private final Map<UUID, List<UUID>> deleted;
    private final Map<UUID, Long> joinedAfter;

    public UUID id() { return id; }

    public String name() { return name; }

    public UUID owner() { return owner; }

    public List<GroupMember> members() { return members; }

    public List<GroupMessage> messages() { return messages; }

    public long sequence() { return sequence; }

    public long revision() { return revision; }

    public Map<UUID, Long> read() { return read; }

    public Map<UUID, List<UUID>> deleted() { return deleted; }

    public Map<UUID, Long> joinedAfter() { return joinedAfter; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GroupRoom)) return false;
        GroupRoom that = (GroupRoom) other;
        return java.util.Objects.equals(this.id, that.id)
                && java.util.Objects.equals(this.name, that.name)
                && java.util.Objects.equals(this.owner, that.owner)
                && java.util.Objects.equals(this.members, that.members)
                && java.util.Objects.equals(this.messages, that.messages)
                && this.sequence == that.sequence
                && this.revision == that.revision
                && java.util.Objects.equals(this.read, that.read)
                && java.util.Objects.equals(this.deleted, that.deleted)
                && java.util.Objects.equals(this.joinedAfter, that.joinedAfter);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(owner);
        hash = 31 * hash + java.util.Objects.hashCode(members);
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        hash = 31 * hash + java.util.Objects.hashCode(sequence);
        hash = 31 * hash + java.util.Objects.hashCode(revision);
        hash = 31 * hash + java.util.Objects.hashCode(read);
        hash = 31 * hash + java.util.Objects.hashCode(deleted);
        hash = 31 * hash + java.util.Objects.hashCode(joinedAfter);
        return hash;
    }

    @Override public String toString() { return "GroupRoom[id=" + id + ", name=" + name + ", owner=" + owner + ", members=" + members + ", messages=" + messages + ", sequence=" + sequence + ", revision=" + revision + ", read=" + read + ", deleted=" + deleted + ", joinedAfter=" + joinedAfter + "]"; }

    /** 原有群成员从建群开始可见；旧格式读取不改变已有成员的历史。 */
    public GroupRoom(UUID id, String name, UUID owner, List<GroupMember> members, List<GroupMessage> messages,
                     long sequence, long revision, Map<UUID, Long> read, Map<UUID, List<UUID>> deleted) {
        this(id, name, owner, members, messages, sequence, revision, read, deleted, ImmutableMap.of());
    }
    public GroupRoom(UUID id, String name, UUID owner, List<GroupMember> members, List<GroupMessage> messages, long sequence, long revision, Map<UUID, Long> read, Map<UUID, List<UUID>> deleted, Map<UUID, Long> joinedAfter) {
        java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(owner);
        if (name == null || Java8.isBlank(name) || name.length() > GroupLimits.NAME_LENGTH
                || members.isEmpty() || members.size() > GroupLimits.MEMBERS
                || messages.size() > GroupLimits.MESSAGES || sequence < 0 || revision < 0)
            throw new GroupFault(GroupStatus.INVALID);
        members = ImmutableList.copyOf(members); messages = ImmutableList.copyOf(messages); read = ImmutableMap.copyOf(read);
        joinedAfter = ImmutableMap.copyOf(joinedAfter);
        deleted = deleted.entrySet().stream().collect(Java8Collections.toMap(Map.Entry::getKey,
                e -> ImmutableList.copyOf(e.getValue())));
        HashSet<UUID> ids = new HashSet<UUID>();
        for (GroupMember member : members) if (!ids.add(member.id())) throw new GroupFault(GroupStatus.INVALID);
        if (!ids.contains(owner) || !ids.containsAll(read.keySet()) || !ids.containsAll(deleted.keySet()) || !ids.containsAll(joinedAfter.keySet()))
            throw new GroupFault(GroupStatus.INVALID);
        long previous = 0;
        HashSet<UUID> messageIds = new HashSet<UUID>();
        for (GroupMessage entry : messages) {
            if (entry.sequence() <= previous || entry.sequence() > sequence || !messageIds.add(entry.message().id()))
                throw new GroupFault(GroupStatus.INVALID);
            previous = entry.sequence();
        }
        for (long value : read.values()) if (value < 0 || value > sequence) throw new GroupFault(GroupStatus.INVALID);
        for (long value : joinedAfter.values()) if (value < 0 || value > sequence) throw new GroupFault(GroupStatus.INVALID);
        for (List<UUID> hidden : deleted.values()) {
            if (hidden.size() > GroupLimits.MESSAGES || new HashSet<>(hidden).size() != hidden.size()
                    || !messageIds.containsAll(hidden)) throw new GroupFault(GroupStatus.INVALID);
        }
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.members = members;
        this.messages = messages;
        this.sequence = sequence;
        this.revision = revision;
        this.read = read;
        this.deleted = deleted;
        this.joinedAfter = joinedAfter;
    }
    public boolean contains(UUID player) { return members.stream().anyMatch(m -> m.id().equals(player)); }
    public void requireMember(UUID player) { if (!contains(player)) throw new GroupFault(GroupStatus.NOT_MEMBER); }
    public List<GroupMessage> visible(UUID player) {
        requireMember(player);
        HashSet<UUID> hidden = new HashSet<>(deleted.getOrDefault(player, ImmutableList.of()));
        long joined = joinedAfter.getOrDefault(player, 0L);
        return messages.stream().filter(m -> m.sequence() > joined && !hidden.contains(m.message().id())).collect(Java8Collections.toList());
    }
    public int unread(UUID player) {
        long seen = read.getOrDefault(player, 0L);
        return (int) visible(player).stream().filter(m -> m.sequence() > seen && !m.message().sender().equals(player)).count();
    }
    public static final Codec<GroupRoom> CODEC = codec(true);
    /** 仅格式 1 没有历史边界；不能把它的缺省语义继续用于格式 2/3。 */
    static final Codec<GroupRoom> LEGACY_CODEC = codec(false);
    private static Codec<GroupRoom> codec(boolean requireJoinedAfter) {
        com.mojang.serialization.codecs.UnboundedMapCodec<UUID,Long> joined = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG);
        return RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(GroupRoom::id), Codec.STRING.fieldOf("name").forGetter(GroupRoom::name),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(GroupRoom::owner), GroupMember.CODEC.listOf().fieldOf("members").forGetter(GroupRoom::members),
            GroupMessage.CODEC.listOf().fieldOf("messages").forGetter(GroupRoom::messages),
            Codec.LONG.fieldOf("sequence").forGetter(GroupRoom::sequence), Codec.LONG.fieldOf("revision").forGetter(GroupRoom::revision),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).fieldOf("read").forGetter(GroupRoom::read),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.CODEC.listOf()).fieldOf("deleted").forGetter(GroupRoom::deleted),
            (requireJoinedAfter ? joined.fieldOf("joined_after") : joined.optionalFieldOf("joined_after", ImmutableMap.of())).forGetter(GroupRoom::joinedAfter)
        ).apply(i, GroupRoom::new));
    }
}
