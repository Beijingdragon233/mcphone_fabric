package com.november.mcphone.platform;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 拿到某个实体的饰品栏，以及「饰品栏上那四件事」。公开签名只出现原版类型，
 * Curios 的类型全部收在这个平台实现里。
 *
 * <p>{@code CuriosCompat} 仍会先判断模组是否在场；只有确认在场后才会进入这里，
 * 因此没有安装 Curios 时不会解析这些第三方类型。</p>
 */
public final class CuriosInventories {

    private CuriosInventories() {}

    /** 饰品栏里的一个位置：槽 id、槽内序号、上面那个物品。 */
    public record Slot(String slotId, int index, ItemStack stack) {}

    public static boolean isEquipped(LivingEntity entity, Predicate<ItemStack> filter) {
        return of(entity).map(inventory -> inventory.isEquipped(filter)).orElse(false);
    }

    public static Optional<Slot> findFirst(LivingEntity entity, Predicate<ItemStack> filter) {
        return of(entity)
                .flatMap(inventory -> inventory.findFirstCurio(filter))
                .map(result -> new Slot(result.slotContext().identifier(),
                        result.slotContext().index(), result.stack()));
    }

    public static ItemStack stackAt(LivingEntity entity, String slotId, int index) {
        return of(entity)
                .flatMap(inventory -> inventory.findCurio(slotId, index))
                .map(result -> result.stack())
                .orElse(ItemStack.EMPTY);
    }

    public static void setEquipped(LivingEntity entity, String slotId, int index, ItemStack stack) {
        of(entity).ifPresent(inventory -> inventory.setEquippedCurio(slotId, index, stack));
    }

    private static Optional<ICuriosItemHandler> of(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity);
    }
}
