package com.november.mcphone.feature.music.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * C2S：对唱片仓做一件事。四个动作包体完全一样，合成一个包。
 * 故意不带"放哪张""从第几秒开始"之类的字段：唱片是仓里那一张，时刻由服务端盖章，不采信客户端。
 */
public final class DiscActionPacket {
    private final Action action;

    public DiscActionPacket(Action action) {
        this.action = action;
    }

    public Action action() { return action; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiscActionPacket)) return false;
        DiscActionPacket that = (DiscActionPacket) other;
        return java.util.Objects.equals(this.action, that.action);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(action);
        return hash;
    }

    @Override public String toString() { return "DiscActionPacket[action=" + action + "]"; }


    public enum Action {
        /** 把主手上的唱片放进去 */
        INSERT,
        EJECT,
        TOGGLE,
        /** 只要一份最新状态，什么都不改。打开音乐 App 时用 */
        QUERY;

        private static final Action[] VALUES = values();

        /** 序号越界一律退回 QUERY：唯一什么都不改的动作，不瞎猜也不抛异常打断连接 */
        static Action decode(int ordinal) {
            return (ordinal >= 0 && ordinal < VALUES.length) ? VALUES[ordinal] : QUERY;
        }
    }

    public static void encode(DiscActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action().ordinal());
    }

    public static DiscActionPacket decode(FriendlyByteBuf buf) {
        return new DiscActionPacket(Action.decode(buf.readVarInt()));
    }
}
