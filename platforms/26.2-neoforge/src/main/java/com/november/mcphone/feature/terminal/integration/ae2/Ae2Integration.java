package com.november.mcphone.feature.terminal.integration.ae2;

import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import com.november.mcphone.feature.terminal.integration.TerminalIntegration;
import com.november.mcphone.feature.terminal.integration.TerminalMods;
import com.november.mcphone.feature.terminal.integration.TerminalSource;
import com.november.mcphone.platform.ModPresence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Applied Energistics 2 terminal integration for 26.2. */
public final class Ae2Integration implements TerminalIntegration {

    public static final String MODID = TerminalMods.AE2_MODID;
    public static final String NAME = TerminalMods.AE2_NAME;

    private static final String AE2WTLIB_MODID = "ae2wtlib";

    @Override
    public String modId() {
        return MODID;
    }

    @Override
    public String displayName() {
        return NAME;
    }

    @Override
    public boolean claims(ItemStack stack) {
        return stack.getItem() instanceof WirelessTerminalItem;
    }

    @Override
    public boolean open(ServerPlayer player, ItemStack stack, TerminalSource source) {
        ItemMenuHostLocator locator = locatorFor(source);

        if (ModPresence.isLoaded(AE2WTLIB_MODID) && Ae2wtlibSupport.isWirelessTerminal(stack)) {
            return Ae2wtlibSupport.open(player, stack, locator);
        }

        if (!(stack.getItem() instanceof WirelessTerminalItem terminal)) return false;
        terminal.openFromInventory(player, locator);
        return true;
    }

    private static ItemMenuHostLocator locatorFor(TerminalSource source) {
        return source.map(
                ignored -> new TerminalSlotLocator(),
                inventorySlot -> MenuLocators.forInventorySlot(inventorySlot.index()));
    }

    @Override
    public void setup() {
        TerminalSlotLocator.register();
    }
}
