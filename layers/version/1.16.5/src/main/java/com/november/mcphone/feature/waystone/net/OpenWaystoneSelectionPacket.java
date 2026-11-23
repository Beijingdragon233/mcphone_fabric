package com.november.mcphone.feature.waystone.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 客户端 → 服务端：玩家在手机里点了「传送石」。
 * 包体故意没字段：服务端从连接上下文取玩家，带玩家 ID 等于允许伪造客户端点开别人的传送点。
 */
public final class OpenWaystoneSelectionPacket {
    public OpenWaystoneSelectionPacket() {
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OpenWaystoneSelectionPacket)) return false;
        return true;
    }

    @Override public int hashCode() {
        return 0;
    }

    @Override public String toString() { return "OpenWaystoneSelectionPacket[]"; }


    public static void encode(OpenWaystoneSelectionPacket msg, FriendlyByteBuf buf) {
    }

    public static OpenWaystoneSelectionPacket decode(FriendlyByteBuf buf) {
        return new OpenWaystoneSelectionPacket();
    }
}
