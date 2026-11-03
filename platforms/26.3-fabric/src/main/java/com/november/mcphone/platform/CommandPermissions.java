package com.november.mcphone.platform;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

/** 26.3 replaced integer operator levels with typed permission sets. */
public final class CommandPermissions {
    private static final Permission ECONOMY_AUDIT =
            new Permission.HasCommandLevel(PermissionLevel.ADMINS);

    private CommandPermissions() {}

    public static boolean canAuditEconomy(CommandSourceStack source) {
        return source.permissions().hasPermission(ECONOMY_AUDIT);
    }
}
