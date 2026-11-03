package com.november.mcphone.core.client;

import com.mojang.serialization.MapCodec;
import com.november.mcphone.core.ModItems;
import com.november.mcphone.core.PhoneItemData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

/** 26.3 的物品模型属性：从旧 predicate 迁移到 range_dispatch。 */
public final class PhoneItemProperties {
    public static final Identifier SCREEN_ON = Identifier.fromNamespaceAndPath("mcphone", "screen_on");

    private PhoneItemProperties() {}

    public static void register(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(SCREEN_ON, ScreenOnProperty.MAP_CODEC);
    }

    /** 无参数属性，值仍直接来自物品组件，保证旁观者看到的状态不变。 */
    public record ScreenOnProperty() implements RangeSelectItemModelProperty {
        public static final MapCodec<ScreenOnProperty> MAP_CODEC = MapCodec.unit(new ScreenOnProperty());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level,
                         @Nullable ItemOwner owner, int seed) {
            return PhoneItemData.isScreenOn(stack) ? 1.0F : 0.0F;
        }

        @Override
        public MapCodec<ScreenOnProperty> type() {
            return MAP_CODEC;
        }
    }
}
