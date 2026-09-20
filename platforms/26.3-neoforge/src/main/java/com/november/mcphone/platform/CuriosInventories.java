package com.november.mcphone.platform;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 拿到某个实体的饰品栏 —— 这一支的答案是「没有」，因为 <b>Curios 到 26.3 还没有构件</b>。
 *
 * 另外三支这里各有一句 {@code CuriosApi.getCuriosInventory}；这一支连
 * {@code ICuriosItemHandler} 都不能写，因为 {@code compileOnly} 是在<b>编译期</b>
 * 要那些类的，而这里拿不到。签名只说原版类型，正是上面那三支也照办之后才成立的事。
 *
 * 四个方法的返回值全都等于「装了 Curios 但那个位置是空的」：{@code false}、空、
 * 空堆、什么都不做。而 {@code CuriosCompat} 在 {@code isLoaded()} 为假时本来
 * 就走不到这里，所以这一份不改任何运行期行为，只是让没有构件的目标也编得出来。
 *
 * 等构件出来：补上 {@code build.gradle} 里那行 {@code compileOnly}，这份照
 * 1.21.1-neoforge 那份改回去即可，两支之间只差 {@code of} 里的一步。
 */
public final class CuriosInventories {

    private CuriosInventories() {}

    /** 饰品栏里的一个位置：槽 id、槽内序号、上面那个物品。 */
    public record Slot(String slotId, int index, ItemStack stack) {}

    /** 这一支永远没有饰品栏，所以永远是 false。 */
    public static boolean isEquipped(LivingEntity entity, Predicate<ItemStack> filter) {
        return false;
    }

    /** 同上，永远找不到。 */
    public static Optional<Slot> findFirst(LivingEntity entity, Predicate<ItemStack> filter) {
        return Optional.empty();
    }

    /** 同上，永远是空堆。 */
    public static ItemStack stackAt(LivingEntity entity, String slotId, int index) {
        return ItemStack.EMPTY;
    }

    /** 同上，什么都不做。 */
    public static void setEquipped(LivingEntity entity, String slotId, int index, ItemStack stack) {
    }
}
