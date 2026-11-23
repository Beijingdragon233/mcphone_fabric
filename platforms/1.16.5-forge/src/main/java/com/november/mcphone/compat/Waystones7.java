package com.november.mcphone.compat;

import net.minecraft.server.level.ServerPlayer;
import net.blay09.mods.waystones.container.WaystoneSelectionContainer;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.blay09.mods.waystones.core.WarpMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

final class Waystones7 {
    private Waystones7() {}

    static boolean openSelection(ServerPlayer player) {
        WarpMode mode = WarpMode.WARP_STONE;
        if (!PlayerWaystoneManager.canUseWarpStone(player, ItemStack.EMPTY)) return false;
        NetworkHooks.openGui(player, new SimpleMenuProvider(
                (id, inventory, owner) -> PhoneWaystoneSelection.create(id, owner),
                Component.translatable("container.waystones.waystone_selection")),
                buffer -> buffer.writeByte(mode.ordinal()));
        return true;
    }
}
