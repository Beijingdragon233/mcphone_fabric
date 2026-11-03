package com.november.mcphone.feature.terminal.integration.ae2;

import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import com.november.mcphone.feature.terminal.TerminalSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/** AE2 locator for a terminal stored in MCphone's terminal slot. */
public record TerminalSlotLocator() implements ItemMenuHostLocator {

    public static void register() {
        MenuLocators.register(
                TerminalSlotLocator.class,
                (locator, buf) -> { },
                buf -> new TerminalSlotLocator());
    }

    @Override
    public ItemStack locateItem(Player player) {
        return TerminalSlot.get(player);
    }

    @Override
    public BlockHitResult hitResult() {
        return null;
    }

    @Override
    public Integer getPlayerInventorySlot() {
        return null;
    }
}
