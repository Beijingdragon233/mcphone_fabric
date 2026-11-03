package com.november.mcphone.core.client;

import com.november.mcphone.MCphone;
import com.november.mcphone.core.ModItems;
import com.november.mcphone.core.PhoneItemData;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

public final class PhoneItemProperties {
    private PhoneItemProperties() {}

    public static final ResourceLocation SCREEN_ON =
            ResourceLocation.fromNamespaceAndPath(MCphone.MODID, "screen_on");

    public static void register() {
        ItemProperties.register(ModItems.PHONE.get(), SCREEN_ON, SCREEN_ON_VALUE);
        ItemProperties.register(ModItems.TABLET.get(), SCREEN_ON, SCREEN_ON_VALUE);
    }

    private static final ClampedItemPropertyFunction SCREEN_ON_VALUE =
            (stack, level, entity, seed) -> PhoneItemData.isScreenOn(stack) ? 1f : 0f;
}
