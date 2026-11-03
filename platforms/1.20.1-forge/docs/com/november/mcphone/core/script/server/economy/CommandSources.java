package com.november.mcphone.core.script.server.economy;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Test-only command source construction for the 1.20.1 permission model. */
final class CommandSources {

    private CommandSources() {}

    static void initialize() {}

    static boolean supportsDispatcherTests() {
        return true;
    }

    static CommandSourceStack forLevel(int level) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, level, "t",
                Component.literal("t"), null, null);
    }
}
