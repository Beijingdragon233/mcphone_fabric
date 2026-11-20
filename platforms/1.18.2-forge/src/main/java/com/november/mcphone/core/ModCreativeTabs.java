package com.november.mcphone.core;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** The phone and tablet group appears alongside the vanilla creative tabs. */
public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final CreativeModeTab MCPHONE_TAB = new CreativeModeTab("mcphone") {
        @Override
        public ItemStack makeIcon() {
            return ModItems.PHONE.get().getDefaultInstance();
        }
    };
}
