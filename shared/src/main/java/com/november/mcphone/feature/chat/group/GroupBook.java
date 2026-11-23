package com.november.mcphone.feature.chat.group;

import com.november.mcphone.util.Java8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.november.mcphone.util.Java8Collections;

import java.util.Set;
import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.util.TextSanitizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.BiPredicate;

/** 群规则与成员索引，全部为可独立测试的业务；网络、SavedData 和头像不进入这里。 */
public final class GroupBook {
    private final Map<UUID, GroupRoom> rooms = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> memberships = new HashMap<>();
    private final GroupInvitations invitations = new GroupInvitations();
    public static GroupBook restore(List<GroupRoom> snapshot) {
        return restore(snapshot, ImmutableList.of());
    }
    public static GroupBook restore(List<GroupRoom> snapshot, List<GroupInvitation> pending) {
        if (snapshot.size() > GroupLimits.GROUPS_PER_WORLD) throw new GroupFault(GroupStatus.LIMIT);
        GroupBook book = new GroupBook();
        for (GroupRoom room : snapshot) {
            if (book.rooms.putIfAbsent(room.id(), room) != null) throw new GroupFault(GroupStatus.INVALID);
            for (GroupMember member : room.members()) {
                LinkedHashSet<UUID> index = book.memberships.computeIfAbsent(member.id(), ignored -> new LinkedHashSet<>());
                if (index.size() >= GroupLimits.GROUPS_PER_PLAYER) throw new GroupFault(GroupStatus.LIMIT);
                index.add(room.id());
            }
        }
        book.invitations.restore(pending);
        for (GroupInvitation invitation : pending) {
            GroupRoom room = book.rooms.get(invitation.group());
            if (room == null || !room.contains(invitation.inviter().id()) || room.contains(invitation.recipient())
                    || room.members().size() + book.invitations.forGroup(room.id()).size() > GroupLimits.MEMBERS)
                throw new GroupFault(GroupStatus.INVALID);
        }
        return book;
    }
    public List<GroupRoom> snapshot() { return ImmutableList.copyOf(rooms.values()); }
    public List<GroupInvitation> invitationSnapshot() { return invitations.snapshot(); }
    public int invitationCount() { return invitations.size(); }
    public List<UUID> pendingRecipients(UUID group) { return invitations.forGroup(group).stream().map(GroupInvitation::recipient).collect(Java8Collections.toList()); }
    public int expireInvitations(long now) { return invitations.expire(now); }
    public List<GroupInviteNotice> notices(UUID actor, long now) {
        return invitations.received(actor).stream().filter(i -> !i.expired(now))
                .map(i -> GroupInviteNotice.of(i, rooms.get(i.group())))
                .sorted(java.util.Comparator.comparingLong(GroupInviteNotice::createdAt).reversed().thenComparing(GroupInviteNotice::id)).collect(Java8Collections.toList());
    }
    /** 仅供同包服务端维护像素引用，不能用作玩家读取入口。 */
    GroupRoom forMaintenance(UUID id) { return rooms.get(id); }
    public List<GroupRoom> groups(UUID player) {
        return memberships.getOrDefault(player, new LinkedHashSet<>()).stream().map(rooms::get).collect(Java8Collections.toList());
    }
    public GroupRoom room(UUID id, UUID actor) {
        GroupRoom room = rooms.get(id);
        if (room == null) throw new GroupFault(GroupStatus.NOT_MEMBER);
        room.requireMember(actor); return room;
    }
    public GroupRoom create(UUID id, GroupMember owner, String rawName, List<GroupMember> invited,
                            Predicate<UUID> friend) {
        return create(id, owner, rawName, invited, friend, System.currentTimeMillis());
    }
    public GroupRoom create(UUID id, GroupMember owner, String rawName, List<GroupMember> invited,
                            Predicate<UUID> friend, long now) {
        String name = Java8.strip(TextSanitizer.sanitize(rawName, GroupLimits.NAME_LENGTH));
        if (name.isEmpty() || invited.isEmpty() || invited.size() + 1 > GroupLimits.MEMBERS || rooms.containsKey(id))
            throw new GroupFault(GroupStatus.INVALID);
        if (rooms.size() >= GroupLimits.GROUPS_PER_WORLD) throw new GroupFault(GroupStatus.LIMIT);
        if (invited.stream().anyMatch(m -> m.id().equals(owner.id()))) throw new GroupFault(GroupStatus.INVALID);
        if (groups(owner.id()).size() >= GroupLimits.GROUPS_PER_PLAYER) throw new GroupFault(GroupStatus.LIMIT);
        GroupRoom room = new GroupRoom(id, name, owner.id(), ImmutableList.of(owner), ImmutableList.of(), 0, 1, ImmutableMap.of(), ImmutableMap.of());
        List<GroupInvitation> planned = invitations.prepare(room, owner, invited, friend, player -> groups(player).size(), now);
        rooms.put(id, room);
        memberships.computeIfAbsent(owner.id(), ignored -> new LinkedHashSet<>()).add(id);
        invitations.add(planned);
        return room;
    }
    public GroupMessage append(UUID id, UUID actor, String senderName, ChatMessage message) {
        GroupRoom room = room(id, actor);
        if (!message.sender().equals(actor) || room.messages().stream().anyMatch(m -> m.message().id().equals(message.id()))
                || room.sequence() == Long.MAX_VALUE) throw new GroupFault(GroupStatus.INVALID);
        GroupMessage entry = new GroupMessage(room.sequence() + 1, senderName, message);
        ArrayList<GroupMessage> messages = new ArrayList<>(room.messages()); messages.add(entry);
        if (messages.size() > GroupLimits.MESSAGES) messages.remove(0);
        Set<UUID> existing = messages.stream().map(m -> m.message().id()).collect(java.util.stream.Collectors.toSet());
        HashMap<UUID,List<UUID>> hidden = new HashMap<UUID, List<UUID>>();
        room.deleted().forEach((player, ids) -> hidden.put(player, ids.stream().filter(existing::contains).collect(Java8Collections.toList())));
        HashMap<UUID,Long> read = new HashMap<>(room.read()); read.put(actor, entry.sequence());
        rooms.put(id, new GroupRoom(id, room.name(), room.owner(), room.members(), messages, entry.sequence(),
                room.revision() + 1, read, hidden, room.joinedAfter()));
        return entry;
    }
    public void markRead(UUID id, UUID actor, long through) {
        GroupRoom room = room(id, actor);
        long next = Math.min(room.sequence(), Math.max(0, through));
        if (next <= room.read().getOrDefault(actor, 0L)) return;
        HashMap<UUID,Long> read = new HashMap<>(room.read()); read.put(actor, next);
        rooms.put(id, new GroupRoom(id, room.name(), room.owner(), room.members(), room.messages(), room.sequence(),
                room.revision(), read, room.deleted(), room.joinedAfter()));
    }
    public boolean hide(UUID id, UUID actor, UUID message) {
        GroupRoom room = room(id, actor);
        if (room.visible(actor).stream().noneMatch(m -> m.message().id().equals(message))) return false;
        HashMap<UUID,List<UUID>> hidden = new HashMap<>(room.deleted());
        LinkedHashSet<UUID> ids = new LinkedHashSet<>(hidden.getOrDefault(actor, ImmutableList.of()));
        if (!ids.add(message)) return true;
        hidden.put(actor, ImmutableList.copyOf(ids));
        rooms.put(id, new GroupRoom(id, room.name(), room.owner(), room.members(), room.messages(), room.sequence(),
                room.revision() + 1, room.read(), hidden, room.joinedAfter())); return true;
    }
    public void rename(UUID id, UUID actor, String raw) {
        GroupRoom room = room(id, actor); requireOwner(room, actor);
        String name = Java8.strip(TextSanitizer.sanitize(raw, GroupLimits.NAME_LENGTH));
        if (name.isEmpty()) throw new GroupFault(GroupStatus.INVALID);
        rooms.put(id, new GroupRoom(id, name, room.owner(), room.members(), room.messages(), room.sequence(),
                room.revision() + 1, room.read(), room.deleted(), room.joinedAfter()));
    }
    /** 创建待处理邀请，不改变成员、未读或入群时间；整批校验成功后才插入邀请索引。 */
    public GroupRoom invite(UUID id, UUID actor, List<GroupMember> invited, Predicate<UUID> friend, long now) {
        GroupRoom room = room(id, actor);
        GroupMember inviter = room.members().stream().filter(m -> m.id().equals(actor)).findFirst().orElseThrow(java.util.NoSuchElementException::new);
        invitations.add(invitations.prepare(room, inviter, invited, friend, player -> groups(player).size(), now)); return room;
    }
    /** 只有被邀请者可处理。接受时再次校验好友、群容量与个人配额，历史边界取同意时的序号。 */
    public GroupRoom respond(UUID group, UUID invitationId, GroupMember actor, boolean accept,
                             BiPredicate<UUID, UUID> friends, long now) {
        GroupInvitation invitation = invitations.get(invitationId);
        if (invitation == null || invitation.expired(now)) throw new GroupFault(GroupStatus.INVITATION_GONE);
        if (!invitation.recipient().equals(actor.id()) || !invitation.group().equals(group)) throw new GroupFault(GroupStatus.NOT_INVITED);
        GroupRoom room = rooms.get(group);
        if (room == null || !room.contains(invitation.inviter().id())) throw new GroupFault(GroupStatus.INVITATION_GONE);
        if (!accept) { invitations.remove(invitationId); return room; }
        if (!friends.test(invitation.inviter().id(), actor.id())) throw new GroupFault(GroupStatus.NOT_FRIEND);
        if (room.contains(actor.id())) throw new GroupFault(GroupStatus.INVALID);
        if (room.members().size() >= GroupLimits.MEMBERS || groups(actor.id()).size() >= GroupLimits.GROUPS_PER_PLAYER)
            throw new GroupFault(GroupStatus.LIMIT);
        ArrayList<GroupMember> members = new ArrayList<>(room.members()); members.add(actor);
        HashMap<UUID,Long> joined = new HashMap<>(room.joinedAfter()); HashMap<UUID,Long> read = new HashMap<>(room.read());
        joined.put(actor.id(), room.sequence()); read.put(actor.id(), room.sequence());
        GroupRoom next = new GroupRoom(group, room.name(), room.owner(), members, room.messages(), room.sequence(),
                room.revision() + 1, read, room.deleted(), joined);
        rooms.put(group, next); memberships.computeIfAbsent(actor.id(), ignored -> new LinkedHashSet<>()).add(group);
        invitations.remove(invitationId);
        return next;
    }
    public void leave(UUID id, UUID actor) {
        GroupRoom room = room(id, actor);
        if (room.owner().equals(actor)) throw new GroupFault(GroupStatus.OWNER_MUST_DISSOLVE);
        HashMap<UUID,Long> read = new HashMap<>(room.read()); read.remove(actor);
        HashMap<UUID,List<UUID>> hidden = new HashMap<>(room.deleted()); hidden.remove(actor);
        HashMap<UUID,Long> joined = new HashMap<>(room.joinedAfter()); joined.remove(actor);
        rooms.put(id, new GroupRoom(id, room.name(), room.owner(), room.members().stream().filter(m -> !m.id().equals(actor)).collect(Java8Collections.toList()),
                room.messages(), room.sequence(), room.revision() + 1, read, hidden, joined));
        removeIndex(actor, id);
        invitations.cancel(id, actor);
    }
    public GroupRoom dissolve(UUID id, UUID actor) {
        GroupRoom room = room(id, actor); requireOwner(room, actor); rooms.remove(id);
        for (GroupMember member : room.members()) removeIndex(member.id(), id);
        invitations.cancel(id, null);
        return room;
    }
    private void removeIndex(UUID actor, UUID id) {
        LinkedHashSet<UUID> index = memberships.get(actor);
        if (index != null) { index.remove(id); if (index.isEmpty()) memberships.remove(actor); }
    }
    private static void requireOwner(GroupRoom room, UUID actor) {
        if (!room.owner().equals(actor)) throw new GroupFault(GroupStatus.OWNER_REQUIRED);
    }
}
