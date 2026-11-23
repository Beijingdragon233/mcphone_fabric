package com.november.mcphone.platform;

import net.minecraft.world.item.ItemStack;

public final class LegacyStacks {
    private LegacyStacks() {}

    public static ItemStack copyOne(ItemStack stack) {
        ItemStack copy = stack.copy();
        if (!copy.isEmpty()) copy.setCount(1);
        return copy;
    }
}
