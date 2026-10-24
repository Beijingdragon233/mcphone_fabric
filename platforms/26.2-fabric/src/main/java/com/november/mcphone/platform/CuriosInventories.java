package com.november.mcphone.platform;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 拿到某个实体的饰品栏，以及「饰品栏上那四件事」。公开签名只出现原版类型，
 * Curios 的类型全部收在这个平台实现里。
 *
 * <p>26.2 没有 Fabric 版 Curios 构件，因此这一支显式报告饰品栏不可用。
 * 这与没有安装 Curios 走同一条路径，不会伪造一个可写的饰品栏。</p>
 */
public final class CuriosInventories {

    private CuriosInventories() {}

    /** 饰品栏里的一个位置：槽 id、槽内序号、上面那个物品。 */
    public record Slot(String slotId, int index, ItemStack stack) {}

    public static boolean isEquipped(LivingEntity entity, Predicate<ItemStack> filter) {
        return false;
    }

    public static Optional<Slot> findFirst(LivingEntity entity, Predicate<ItemStack> filter) {
        return Optional.empty();
    }

    public static ItemStack stackAt(LivingEntity entity, String slotId, int index) {
        return ItemStack.EMPTY;
    }

    public static void setEquipped(LivingEntity entity, String slotId, int index, ItemStack stack) {
        // No Fabric 26.2 Curios runtime exists.
    }
}
