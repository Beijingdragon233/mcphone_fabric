package com.november.mcphone.feature.chat.net;


import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

/** "加联系人"界面里的一行（一个在线玩家）；relation 决定这一行显示什么按钮。 */
public final class OnlinePlayer {
    private final UUID id;
    private final String name;
    private final Relation relation;

    public OnlinePlayer(UUID id, String name, Relation relation) {
        this.id = id;
        this.name = name;
        this.relation = relation;
    }

    public UUID id() { return id; }

    public String name() { return name; }

    public Relation relation() { return relation; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OnlinePlayer)) return false;
        OnlinePlayer that = (OnlinePlayer) other;
        return java.util.Objects.equals(this.id, that.id)
                && java.util.Objects.equals(this.name, that.name)
                && java.util.Objects.equals(this.relation, that.relation);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(relation);
        return hash;
    }

    @Override public String toString() { return "OnlinePlayer[id=" + id + ", name=" + name + ", relation=" + relation + "]"; }


    public static void encode(OnlinePlayer msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.id());
        buf.writeUtf(msg.name(), ConversationSummary.MAX_NAME_LENGTH);
        Relation.encode(msg.relation(), buf);
    }

    public static OnlinePlayer decode(FriendlyByteBuf buf) {
        return new OnlinePlayer(
                buf.readUUID(),
                buf.readUtf(ConversationSummary.MAX_NAME_LENGTH),
                Relation.decode(buf));
    }
}
