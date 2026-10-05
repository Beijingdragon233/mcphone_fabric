package com.november.mcphone.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

import java.util.Set;

public final class ServerTeleports {
    private ServerTeleports() {}

    public static void teleport(ServerPlayer player, ServerPlayer target) {
        player.teleportTo(target.level(), target.getX(), target.getY(), target.getZ(),
                Set.of(), target.getYRot(), target.getXRot(), true);
    }
}
