package com.november.mcphone.feature.terminal.integration.ae2;

import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** AE2WTLib must use its own menu type instead of AE2's generic terminal entry. */
final class Ae2wtlibSupport {

    private Ae2wtlibSupport() {}

    static boolean isWirelessTerminal(ItemStack stack) {
        return stack.getItem() instanceof ItemWT;
    }

    static boolean open(ServerPlayer player, ItemStack stack, ItemMenuHostLocator locator) {
        if (!(stack.getItem() instanceof ItemWT terminal)) return false;
        return terminal.tryOpen(player, locator, false);
    }
}
