package com.november.mcphone.feature.terminal.integration.ae2;

import appeng.api.features.IWirelessTermHandler;
import appeng.helpers.WirelessTerminalGuiObject;
import com.november.mcphone.core.PhoneItem;
import com.november.mcphone.feature.terminal.TerminalSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** AE2 8 host backed by the actual capability stack on both sides of the connection. */
public final class PhoneWirelessHost extends WirelessTerminalGuiObject {
    public static final int PHONE_SLOT = Integer.MAX_VALUE;
    private final Player owner;

    public PhoneWirelessHost(IWirelessTermHandler handler, ItemStack stack, Player owner) {
        super(handler, stack, owner, PHONE_SLOT);
        this.owner = owner;
    }

    public boolean stillInstalled() {
        return !getItemStack().isEmpty() && TerminalSlot.get(owner) == getItemStack()
                && PhoneItem.isCarriedBy(owner);
    }
}
