package com.november.mcphone.feature.terminal.integration.ae2;

import appeng.container.ContainerLocator;
import appeng.container.ContainerOpener;
import appeng.container.me.items.WirelessTermContainer;
import appeng.core.Api;
import appeng.core.localization.PlayerMessages;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.november.mcphone.feature.terminal.integration.TerminalIntegration;
import com.november.mcphone.feature.terminal.integration.TerminalSource;
import com.november.mcphone.feature.terminal.TerminalSlot;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class Ae2Integration implements TerminalIntegration {
    public static final String MODID = "appliedenergistics2";
    public static final String NAME = "Applied Energistics 2";

    @Override public String modId() { return MODID; }
    @Override public String displayName() { return NAME; }
    @Override public boolean claims(ItemStack stack) { return stack.getItem() instanceof WirelessTerminalItem; }

    @Override
    public boolean open(ServerPlayer player, ItemStack stack, TerminalSource source) {
        if (!claims(stack)) return false;
        WirelessTerminalItem terminal = (WirelessTerminalItem) stack.getItem();
        int index;
        if (source instanceof TerminalSource.PhoneSlot) {
            if (TerminalSlot.get(player) != stack) return false;
            index = PhoneWirelessHost.PHONE_SLOT;
            TerminalSlot.markChanged(player);
        } else if (source instanceof TerminalSource.InventorySlot) {
            index = ((TerminalSource.InventorySlot) source).index();
            if (player.getInventory().getItem(index) != stack) return false;
        } else return false;

        String key = terminal.getEncryptionKey(stack);
        if (key.isEmpty()) return reject(player, PlayerMessages.DeviceNotLinked);
        long station;
        try {
            station = Long.parseLong(key);
        } catch (NumberFormatException invalid) {
            return reject(player, PlayerMessages.DeviceNotLinked);
        }
        if (Api.instance().registries().locatable().getLocatableBy(station) == null) {
            return reject(player, PlayerMessages.StationCanNotBeLocated);
        }
        if (!terminal.hasPower(player, 0.5, stack)) return reject(player, PlayerMessages.DeviceNotPowered);
        return ContainerOpener.openContainer(WirelessTermContainer.TYPE, player, inventoryLocator(index));
    }

    private static boolean reject(ServerPlayer player, PlayerMessages message) {
        player.displayClientMessage(message.get(), true);
        return false;
    }

    private static ContainerLocator inventoryLocator(int index) {
        // AE2 8 has a public wire decoder for player inventory slots, but only a hand-based factory.
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(5));
        try {
            buffer.writeByte(0);
            buffer.writeInt(index);
            return ContainerLocator.read(buffer);
        } finally {
            buffer.release();
        }
    }
}
