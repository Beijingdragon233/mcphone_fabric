package com.november.mcphone.test;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 保留同一权限等级的命令测试夹具，断言和命令本身不随平台变化。 */
public final class CommandPermissions {
    private CommandPermissions() {}

    public static int level(int value) {
        return value;
    }

    public static CommandSourceStack source(int level) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, level, "t",
                Component.literal("t"), null, null);
    }

    public static boolean supportsDispatcherTests() {
        return true;
    }
}
