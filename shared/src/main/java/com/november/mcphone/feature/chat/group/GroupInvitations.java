package com.november.mcphone.feature.chat.group;

import com.google.common.collect.ImmutableList;
import com.november.mcphone.util.Java8Collections;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** 邀请索引及批次准备。这里只存待处理记录；加入成员与存档事务由 GroupBook 协调。 */
final class GroupInvitations {
    private final Map<UUID, GroupInvitation> records = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> groups = new HashMap<>(), recipients = new HashMap<>();
    List<GroupInvitation> snapshot() { return ImmutableList.copyOf(records.values()); }
    List<GroupInvitation> forGroup(UUID group) { return list(groups, group); }
    List<GroupInvitation> received(UUID actor) { return list(recipients, actor); }
    GroupInvitation get(UUID id) { return records.get(id); }
    int size() { return records.size(); }
    private List<GroupInvitation> list(Map<UUID, LinkedHashSet<UUID>> index, UUID key) {
        return index.getOrDefault(key, new LinkedHashSet<>()).stream().map(records::get).collect(Java8Collections.toList());
    }
    void restore(List<GroupInvitation> snapshot) {
        if (snapshot.size() > GroupLimits.INVITATIONS_PER_WORLD) throw new GroupFault(GroupStatus.LIMIT);
        for (GroupInvitation invitation : snapshot) {
            if (records.containsKey(invitation.id()) || received(invitation.recipient()).size() >= GroupLimits.INVITATIONS_PER_PLAYER
                    || forGroup(invitation.group()).stream().anyMatch(i -> i.recipient().equals(invitation.recipient()))) throw new GroupFault(GroupStatus.INVALID);
            add(ImmutableList.of(invitation));
        }
    }
    List<GroupInvitation> prepare(GroupRoom room, GroupMember inviter, List<GroupMember> requested, Predicate<UUID> friend,
                                  ToIntFunction<UUID> memberships, long now) {
        if (requested.isEmpty() || requested.size() >= GroupLimits.MEMBERS || now < 0 || now > Long.MAX_VALUE - GroupLimits.INVITATION_LIFETIME_MS)
            throw new GroupFault(GroupStatus.INVALID);
        List<GroupInvitation> pending = forGroup(room.id()); ArrayList<GroupMember> targets = new ArrayList<GroupMember>(); HashSet<UUID> unique = new HashSet<UUID>();
        for (GroupMember member : requested) {
            if (!unique.add(member.id())) throw new GroupFault(GroupStatus.INVALID);
            if (room.contains(member.id())) continue;
            if (!friend.test(member.id())) throw new GroupFault(GroupStatus.NOT_FRIEND);
            if (pending.stream().anyMatch(i -> i.recipient().equals(member.id()))) continue;
            if (memberships.applyAsInt(member.id()) >= GroupLimits.GROUPS_PER_PLAYER || received(member.id()).size() >= GroupLimits.INVITATIONS_PER_PLAYER)
                throw new GroupFault(GroupStatus.LIMIT);
            targets.add(member);
        }
        if (room.members().size() + pending.size() + targets.size() > GroupLimits.MEMBERS
                || records.size() + targets.size() > GroupLimits.INVITATIONS_PER_WORLD) throw new GroupFault(GroupStatus.LIMIT);
        ArrayList<GroupInvitation> result = new ArrayList<GroupInvitation>(); HashSet<UUID> ids = new HashSet<>(records.keySet());
        for (GroupMember target : targets) {
            UUID id; do { id = UUID.randomUUID(); } while (!ids.add(id));
            result.add(new GroupInvitation(id, room.id(), inviter, target.id(), now, now + GroupLimits.INVITATION_LIFETIME_MS));
        }
        return ImmutableList.copyOf(result);
    }
    void add(List<GroupInvitation> invitations) {
        for (GroupInvitation invitation : invitations) {
            records.put(invitation.id(), invitation);
            groups.computeIfAbsent(invitation.group(), ignored -> new LinkedHashSet<>()).add(invitation.id());
            recipients.computeIfAbsent(invitation.recipient(), ignored -> new LinkedHashSet<>()).add(invitation.id());
        }
    }
    void remove(UUID id) {
        GroupInvitation invitation = records.remove(id); if (invitation == null) return;
        removeIndex(groups, invitation.group(), id); removeIndex(recipients, invitation.recipient(), id);
    }
    private static void removeIndex(Map<UUID, LinkedHashSet<UUID>> index, UUID key, UUID id) {
        LinkedHashSet<UUID> entries = index.get(key); if (entries != null) { entries.remove(id); if (entries.isEmpty()) index.remove(key); }
    }
    void cancel(UUID group, UUID inviter) {
        forGroup(group).stream().filter(i -> inviter == null || i.inviter().id().equals(inviter)).map(GroupInvitation::id).collect(Java8Collections.toList()).forEach(this::remove);
    }
    int expire(long now) {
        List<UUID> expired = records.values().stream().filter(i -> i.expired(now)).map(GroupInvitation::id).collect(Java8Collections.toList());
        expired.forEach(this::remove); return expired.size();
    }
}
