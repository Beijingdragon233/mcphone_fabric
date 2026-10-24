package com.november.mcphone.platform;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** 26.3 的注册表 get 返回 Optional<Reference<T>>，共用层只需要 Item。 */
public final class RegistryItems {

    private RegistryItems() {}

    public static Item get(Identifier id) {
        return BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(Items.AIR);
    }
}
