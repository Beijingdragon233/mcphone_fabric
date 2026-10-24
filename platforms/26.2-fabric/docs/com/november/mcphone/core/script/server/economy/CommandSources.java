package com.november.mcphone.core.script.server.economy;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Test-only command source construction for the 26.2 permission model. */
final class CommandSources {

    private CommandSources() {}

    static void initialize() {}

    /** Brigadier's static command tree needs a real FML Loader on 26.2. */
    static boolean supportsDispatcherTests() {
        return false;
    }

    static CommandSourceStack forLevel(int level) {
        PermissionLevel permissionLevel = switch (level) {
            case 0 -> PermissionLevel.ALL;
            case 1 -> PermissionLevel.MODERATORS;
            case 2 -> PermissionLevel.GAMEMASTERS;
            case 3 -> PermissionLevel.ADMINS;
            default -> PermissionLevel.OWNERS;
        };
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null,
                null, "t", Component.literal("t"), null, null)
                .withPermission(permission -> permission instanceof Permission.HasCommandLevel required
                        && permissionLevel.ordinal() >= required.level().ordinal());
    }
}
