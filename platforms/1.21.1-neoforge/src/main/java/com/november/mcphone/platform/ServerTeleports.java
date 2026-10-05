package com.november.mcphone.platform;

import net.minecraft.server.level.ServerPlayer;

public final class ServerTeleports {
    private ServerTeleports() {}

    public static void teleport(ServerPlayer player, ServerPlayer target) {
        player.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(),
                target.getYRot(), target.getXRot());
    }
}
