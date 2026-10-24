package com.november.mcphone.compat;

import com.november.mcphone.MCphone;
import com.november.mcphone.platform.ModPresence;
import net.blay09.mods.balm.Balm;
import net.blay09.mods.waystones.menu.WaystoneSelectionListBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Waystones 26.3 compatibility entry point.
 *
 * <p>All third-party types stay inside the two methods below. {@link #isLoaded()}
 * deliberately touches only MCphone's platform facade so a missing optional mod
 * cannot make the app's availability check itself fail to load.</p>
 */
public final class WaystonesCompat {

    public static final String WAYSTONES_MODID = "waystones";

    private WaystonesCompat() {}

    public static boolean isLoaded() {
        return ModPresence.isLoaded(WAYSTONES_MODID);
    }

    public static boolean openSelection(ServerPlayer player) {
        if (!isLoaded()) return false;

        try {
            openSelectionInternal(player);
            return true;
        } catch (Throwable t) {
            MCphone.LOGGER.error("打开传送石选点界面失败（Waystones 版本可能不兼容）", t);
            return false;
        }
    }

    private static void openSelectionInternal(ServerPlayer player) {
        var menuProvider = new WaystoneSelectionListBuilder(player)
                .withTargetsForItem(warpStoneStack())
                .buildMenuProvider(
                        net.blay09.mods.waystones.menu.ModMenus.warpStoneSelection.value(),
                        Component.translatable("container.waystones.waystone_selection"));

        Balm.networking().openMenu(player, menuProvider);
    }

    private static ItemStack warpStoneStack() {
        Item item = BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath(WAYSTONES_MODID, "warp_stone"));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
