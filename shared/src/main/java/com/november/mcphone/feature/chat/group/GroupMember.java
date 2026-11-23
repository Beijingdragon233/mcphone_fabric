package com.november.mcphone.feature.chat.group;

import com.november.mcphone.util.Java8;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import java.util.UUID;

/** 名称由服务端已知玩家资料产生，离线成员仍可显示。 */
public final class GroupMember {
    private final UUID id;
    private final String name;

    public UUID id() { return id; }

    public String name() { return name; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GroupMember)) return false;
        GroupMember that = (GroupMember) other;
        return java.util.Objects.equals(this.id, that.id)
                && java.util.Objects.equals(this.name, that.name);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        return hash;
    }

    @Override public String toString() { return "GroupMember[id=" + id + ", name=" + name + "]"; }

    public GroupMember(UUID id, String name) {
        java.util.Objects.requireNonNull(id);
        if (name == null || Java8.isBlank(name) || name.length() > GroupLimits.NAME_LENGTH) throw new GroupFault(GroupStatus.INVALID);
        this.id = id;
        this.name = name;
    }
    public static final Codec<GroupMember> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(GroupMember::id),
            Codec.STRING.fieldOf("name").forGetter(GroupMember::name)).apply(i, GroupMember::new));
}
