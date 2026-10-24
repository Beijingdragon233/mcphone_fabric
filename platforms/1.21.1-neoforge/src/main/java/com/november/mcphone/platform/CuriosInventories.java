package com.november.mcphone.platform;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 拿到某个实体的饰品栏，以及「饰品栏上那四件事」—— 全仓唯一碰 Curios 的地方。
 *
 * <h2>为什么这一层连 {@code ICuriosItemHandler} 都不能往外递</h2>
 *
 * 早先这里只有一个 {@code of(entity)}，把 Curios 的 handler 交回给
 * {@code CuriosCompat} 去 {@code map / flatMap}。那样这支接缝的<b>签名</b>就带着
 * Curios 的类型，于是 {@code CuriosCompat} 虽然没有一行 Curios import，也照样
 * 必须在编译期看得见 Curios —— 装了别的版本「没有 Curios 构件」的目标就卡死在这里。
 *
 * 现在四个操作整个搬进来，往外只递 {@code boolean / Optional<Slot> / ItemStack}
 * 这些原版类型。{@code CuriosCompat} 因此在不装 Curios 的目标上也编得过；
 * 那里这一支的 {@code Slot} 根本不会被造出来。
 *
 * <h2>两个加载器的差异，收在 {@code of} 一个方法里</h2>
 *
 * 两支给的东西不同，但差的只有下一步。方法名与后面那一串
 * {@code map / flatMap / ifPresent} 全都一样，<b>差的只是 {@code of} 里那一步</b>。
 *
 * <h2>调用方仍然要先判在不在场</h2>
 *
 * 这一层<b>不判 Curios 装没装</b>。{@code CuriosCompat} 四个公开方法各自那句
 * {@code ModPresence.isLoaded} 才是挡在前面的一道 —— 判断和真调用分在两个方法里，
 * 是因为 JVM 在准备执行一个方法时会解析它引用到的类型，写在同一个方法里的话
 * 那句 if 还没来得及执行，方法本身就可能因为解析不了 Curios 而抛
 * {@code NoClassDefFoundError}。现在「真调用」这一半就是这里，别把它挪回 shared。
 */
public final class CuriosInventories {

    private CuriosInventories() {}

    /** 饰品栏里的一个位置：槽 id、槽内序号、上面那个物品。 */
    public record Slot(String slotId, int index, ItemStack stack) {}

    /** 饰品栏里有没有符合条件的物品。<b>调用前必须已经确认 Curios 在场。</b> */
    public static boolean isEquipped(LivingEntity entity, Predicate<ItemStack> filter) {
        return of(entity).map(inventory -> inventory.isEquipped(filter)).orElse(false);
    }

    /** 第一个符合条件的物品在哪个位置；没有则空。<b>调用前必须已经确认 Curios 在场。</b> */
    public static Optional<Slot> findFirst(LivingEntity entity, Predicate<ItemStack> filter) {
        return of(entity)
                .flatMap(inventory -> inventory.findFirstCurio(filter))
                .map(result -> new Slot(result.slotContext().identifier(),
                        result.slotContext().index(), result.stack()));
    }

    /** 某个位置上的物品；位置不存在时返回空堆。<b>调用前必须已经确认 Curios 在场。</b> */
    public static ItemStack stackAt(LivingEntity entity, String slotId, int index) {
        return of(entity)
                .flatMap(inventory -> inventory.findCurio(slotId, index))
                .map(result -> result.stack())
                .orElse(ItemStack.EMPTY);
    }

    /**
     * 把物品写回某个位置。
     *
     * 直接改 {@link #stackAt} 拿到的那个 ItemStack 其实也能改到数据，但改完
     * 客户端未必看得见——饰品栏的同步归 Curios 管，走 {@code setEquippedCurio}
     * 就是在通知它。
     */
    public static void setEquipped(LivingEntity entity, String slotId, int index, ItemStack stack) {
        of(entity).ifPresent(inventory -> inventory.setEquippedCurio(slotId, index, stack));
    }

    private static Optional<ICuriosItemHandler> of(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity);
    }
}
