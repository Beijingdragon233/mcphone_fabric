package com.november.mcphone.platform;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** 26.1 的注册表 get 由编译绑定改名为 getValue。 */
public final class RegistryItems {
    private RegistryItems() {}

    public static Item get(Identifier id) {
        return BuiltInRegistries.ITEM.get(id);
    }
}
