package com.november.mcphone.platform;

import net.minecraft.commands.CommandSourceStack;

/** Platform permission checks kept out of the shared command tree. */
public final class CommandPermissions {
    private CommandPermissions() {}

    public static boolean canAuditEconomy(CommandSourceStack source) {
        return source.hasPermission(3);
    }
}
