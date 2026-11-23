package com.november.mcphone.feature.terminal.integration.refinedstorage;

import com.november.mcphone.feature.terminal.integration.TerminalIntegration;
import com.november.mcphone.feature.terminal.integration.TerminalSource;
import com.november.mcphone.feature.terminal.TerminalSlot;
import com.refinedmods.refinedstorage.api.network.grid.IGridManager;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.apiimpl.network.grid.factory.PortableGridGridFactory;
import com.refinedmods.refinedstorage.inventory.player.PlayerSlot;
import com.refinedmods.refinedstorage.item.NetworkItem;
import com.refinedmods.refinedstorage.item.blockitem.PortableGridBlockItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** RS 1.9 keeps its wire slot format; a private slot marker resolves the real phone capability. */
public final class RefinedStorageIntegration implements TerminalIntegration {
    public static final String MODID = "refinedstorage";
    public static final String NAME = "Refined Storage";
    public static final int PHONE_SLOT = Integer.MAX_VALUE;

    @Override public String modId() { return MODID; }
    @Override public String displayName() { return NAME; }
    @Override public boolean claims(ItemStack stack) {
        return stack.getItem() instanceof NetworkItem || stack.getItem() instanceof PortableGridBlockItem;
    }

    @Override public boolean open(ServerPlayer player, ItemStack stack, TerminalSource source) {
        if (!claims(stack)) return false;
        PlayerSlot slot;
        if (source instanceof TerminalSource.PhoneSlot) {
            if (TerminalSlot.get(player) != stack) return false;
            TerminalSlot.markChanged(player);
            slot = new PlayerSlot(PHONE_SLOT);
        } else if (source instanceof TerminalSource.InventorySlot) {
            int index = ((TerminalSource.InventorySlot) source).index();
            if (player.getInventory().getItem(index) != stack) return false;
            slot = new PlayerSlot(index);
        } else return false;
        if (stack.getItem() instanceof NetworkItem) {
            ((NetworkItem) stack.getItem()).applyNetwork(player.server, stack,
                    network -> network.getNetworkItemManager().open(player, stack, slot),
                    component -> player.displayClientMessage(component, false));
            return true;
        }
        IGridManager grids = API.instance().getGridManager();
        grids.openGrid(PortableGridGridFactory.ID, player, stack, slot);
        return true;
    }
}