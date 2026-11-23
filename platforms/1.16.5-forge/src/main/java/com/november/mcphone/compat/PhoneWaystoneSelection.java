package com.november.mcphone.compat;

import com.november.mcphone.core.PhoneItem;
import net.blay09.mods.waystones.container.WaystoneSelectionContainer;
import net.blay09.mods.waystones.core.WarpMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

/** Marks only server menus opened by the phone. Ordinary Waystones menus keep their own item checks. */
public final class PhoneWaystoneSelection extends WaystoneSelectionContainer {
    private PhoneWaystoneSelection(int id, WaystoneSelectionContainer original) {
        super((MenuType<WaystoneSelectionContainer>) original.getType(), WarpMode.WARP_STONE,
                null, id, original.getWaystones());
    }

    public static PhoneWaystoneSelection create(int id, Player owner) {
        return new PhoneWaystoneSelection(id, createWaystoneSelection(id, owner, WarpMode.WARP_STONE, null));
    }

    @Override public boolean stillValid(Player owner) {
        return PhoneItem.isCarriedBy(owner) && super.stillValid(owner);
    }
}
