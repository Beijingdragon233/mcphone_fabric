package com.november.mcphone.feature.chat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.november.mcphone.util.Java8Collections;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** 个人可见性的纯规则。原消息不变，任一方删除只修改自己的标记。 */
final class ChatMessageVisibility {
    private static final class Key {
        private final UUID owner;
        private final ConversationKey conversation;

        private Key(UUID owner, ConversationKey conversation) {
            this.owner = owner;
            this.conversation = conversation;
        }

        public UUID owner() { return owner; }

        public ConversationKey conversation() { return conversation; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key)) return false;
            Key that = (Key) other;
            return java.util.Objects.equals(this.owner, that.owner)
                    && java.util.Objects.equals(this.conversation, that.conversation);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(owner);
            hash = 31 * hash + java.util.Objects.hashCode(conversation);
            return hash;
        }

        @Override public String toString() { return "Key[owner=" + owner + ", conversation=" + conversation + "]"; }
}
    private final Map<Key, Set<UUID>> hidden = new HashMap<>();
    public boolean hide(UUID owner, UUID peer, UUID message) {
        return hidden.computeIfAbsent(new Key(owner, ConversationKey.of(owner, peer)), k -> new HashSet<>()).add(message);
    }
    public boolean visible(UUID owner, UUID peer, UUID message) {
        return !hidden.getOrDefault(new Key(owner, ConversationKey.of(owner, peer)), ImmutableSet.of()).contains(message);
    }
    public List<ChatMessage> filter(UUID owner, UUID peer, List<ChatMessage> messages) {
        return messages.stream().filter(m -> visible(owner, peer, m.id())).collect(Java8Collections.toList());
    }
    public boolean retain(UUID a, UUID b, Set<UUID> existing) {
        boolean changed = false;
        ConversationKey conversation = ConversationKey.of(a, b);
        Iterator<Entry<ChatMessageVisibility.Key,Set<UUID>>> iterator = hidden.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<ChatMessageVisibility.Key,Set<UUID>> entry = iterator.next();
            if (!entry.getKey().conversation().equals(conversation)) continue;
            changed |= entry.getValue().retainAll(existing);
            if (entry.getValue().isEmpty()) iterator.remove();
        }
        return changed;
    }
    public Map<String, List<UUID>> snapshot() {
        TreeMap<String,List<UUID>> result = new TreeMap<String, List<UUID>>();
        hidden.forEach((k, v) -> result.put(k.owner() + ";" + k.conversation().toStorageKey(), v.stream().sorted().collect(Java8Collections.toList())));
        return ImmutableMap.copyOf(result);
    }
    public static ChatMessageVisibility restore(Map<String, List<UUID>> snapshot) {
        ChatMessageVisibility state = new ChatMessageVisibility();
        snapshot.forEach((key, ids) -> {
            int delimiter = key.indexOf(';');
            if (delimiter < 0) throw new IllegalArgumentException("删除标记的会话键无效");
            UUID owner = UUID.fromString(key.substring(0, delimiter));
            ConversationKey conversation = ConversationKey.parse(key.substring(delimiter + 1));
            if (conversation == null || (!conversation.lo().equals(owner) && !conversation.hi().equals(owner)))
                throw new IllegalArgumentException("删除标记的所有者不属于会话");
            state.hidden.put(new Key(owner, conversation), new HashSet<>(ids));
        });
        return state;
    }
}
