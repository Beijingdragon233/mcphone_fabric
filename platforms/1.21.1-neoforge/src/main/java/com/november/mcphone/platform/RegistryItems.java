package com.november.mcphone.platform;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class RegistryItems {

    private RegistryItems() {}

    public static Item get(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }
}
