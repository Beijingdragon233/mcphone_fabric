package com.november.mcphone.feature.terminal.integration.ae2;

import appeng.api.features.IWirelessTermHandler;
import appeng.container.ContainerLocator;
import appeng.container.ContainerOpener;
import appeng.container.me.items.WirelessTermContainer;
import appeng.core.Api;
import appeng.core.localization.PlayerMessages;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.november.mcphone.feature.terminal.integration.TerminalIntegration;
import com.november.mcphone.feature.terminal.integration.TerminalSource;
import com.november.mcphone.feature.terminal.TerminalSlot;
import com.november.mcphone.platform.ModPresence;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class Ae2Integration implements TerminalIntegration {
    public static final String MODID = "appliedenergistics2";
    public static final String NAME = "Applied Energistics 2";
    /** AE2WTLib（REDUX 4.0.2）沿用上游 modid；软依赖，没有它时这条分支不会走到。 */
    private static final String AE2WTLIB_MODID = "ae2wtlib";

    @Override public String modId() { return MODID; }
    @Override public String displayName() { return NAME; }

    /**
     * AE2 自己的无线终端，加上 AE2WTLib 那一系。两者必须分开认：AE2WTLib 的终端继承的是
     * {@code AEBasePoweredItem}，不是 {@code WirelessTerminalItem}。
     */
    @Override public boolean claims(ItemStack stack) {
        if (stack.getItem() instanceof WirelessTerminalItem) return true;
        return hasAe2wtlib() && Ae2wtlibSupport.isTerminal(stack);
    }

    @Override
    public boolean open(ServerPlayer player, ItemStack stack, TerminalSource source) {
        if (!claims(stack)) return false;
        boolean phone = source instanceof TerminalSource.PhoneSlot;
        int index;
        if (phone) {
            if (TerminalSlot.get(player) != stack) return false;
            index = PhoneWirelessHost.PHONE_SLOT;
            TerminalSlot.markChanged(player);
        } else if (source instanceof TerminalSource.InventorySlot) {
            index = ((TerminalSource.InventorySlot) source).index();
            if (player.getInventory().getItem(index) != stack) return false;
        } else return false;

        // 绑定与定位两家都查；电量交给各自的开法。
        IWirelessTermHandler handler = (IWirelessTermHandler) stack.getItem();
        if (!linkedAndLocatable(player, stack, handler)) return false;

        if (stack.getItem() instanceof WirelessTerminalItem) {
            // AE2 自己的终端仍按 0.5 预检。
            if (!handler.hasPower(player, 0.5, stack)) return reject(player, PlayerMessages.DeviceNotPowered);
            return ContainerOpener.openContainer(WirelessTermContainer.TYPE, player, inventoryLocator(index));
        }

        // AE2WTLib：不做桥自己的 0.5 预检，交给上游 opener/容器判定（上游 open 预检也是 0.5，
        // 容器运行时只要求 extractAEPower(1.0, SIMULATE) != 0）。返回真的换上了菜单才算成功。
        if (phone) return Ae2wtlibSupport.openFromPhone(player, stack);
        return Ae2wtlibSupport.openFromInventory(player, stack, inventoryLocator(index));
    }

    private static boolean linkedAndLocatable(ServerPlayer player, ItemStack stack, IWirelessTermHandler handler) {
        String key = handler.getEncryptionKey(stack);
        if (key == null || key.isEmpty()) return reject(player, PlayerMessages.DeviceNotLinked);
        long station;
        try {
            station = Long.parseLong(key);
        } catch (NumberFormatException invalid) {
            return reject(player, PlayerMessages.DeviceNotLinked);
        }
        if (Api.instance().registries().locatable().getLocatableBy(station) == null) {
            return reject(player, PlayerMessages.StationCanNotBeLocated);
        }
        return true;
    }

    private static boolean reject(ServerPlayer player, PlayerMessages message) {
        player.displayClientMessage(message.get(), true);
        return false;
    }

    private static ContainerLocator inventoryLocator(int index) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(5));
        try {
            buffer.writeByte(0);
            buffer.writeInt(index);
            return ContainerLocator.read(buffer);
        } finally {
            buffer.release();
        }
    }

    private static boolean hasAe2wtlib() {
        return ModPresence.isLoaded(AE2WTLIB_MODID);
    }
}