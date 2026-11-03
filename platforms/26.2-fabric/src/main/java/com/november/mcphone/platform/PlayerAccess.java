package com.november.mcphone.platform;

import com.november.mcphone.platform.client.ClientMessages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 玩家名称、提示、权限与传送的版本接缝。 */
public final class PlayerAccess {
    private PlayerAccess() {}

    public static String name(Player p) {
        return Profiles.name(p.getGameProfile());
    }

    public static Optional<String> cachedName(MinecraftServer server, UUID id) {
        return Profiles.cachedName(server, id);
    }

    public static void message(Player p, Component text, boolean overlay) {
        ClientMessages.show(p, text, overlay);
    }

    public static boolean admin(CommandSourceStack source) {
        return CommandPermissions.canAuditEconomy(source);
    }

    public static Component itemName(ItemLike item) {
        return new ItemStack(item).getHoverName();
    }

    public static void teleport(ServerPlayer player, ServerLevel level,
                                double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, true);
    }
}
