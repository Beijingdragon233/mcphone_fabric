package com.november.mcphone.compat;

import com.november.mcphone.platform.ModPresence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.november.mcphone.platform.CuriosInventories;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Curios（饰品栏）兼容层。
 *
 * 为什么所有 Curios 调用都关在这一个类里
 *
 * Curios 是【可选】依赖：装了就能把手机挂腰上，没装则一切照旧。
 * build.gradle 里用的是 compileOnly，意味着编译期有这些类、运行期
 * 可能一个都没有。
 *
 * JVM 在准备执行一个方法时会解析它引用到的类型，碰上不存在的类就抛
 * NoClassDefFoundError。所以判断"装没装"和"真去调它"必须分在【两个
 * 方法】里：写在同一个方法里的话，那句 if 还没来得及执行，方法本身
 * 就可能因为解析不了 CuriosApi 而炸掉。
 *
 * 这里只做前半件。后半件在 platform/CuriosInventories —— 四个操作整个搬了过去，
 * 那边返回的只有原版类型，Curios 的 handler 不出那个文件。于是这一份在
 * 【没有 Curios 构件的目标】上也编得过（26.3 就是），四个公开方法各自
 * 只剩"判在不在场 + 委托"。
 *
 * 关在一处的好处是这条规矩只需在这里守住，外面的代码照常写。
 */
public final class CuriosCompat {

    private CuriosCompat() {}

    private static final String CURIOS_MODID = "curios";

    /**
     * 装没装 Curios。
     *
     * 不缓存：ModPresence 底下就是一次 map 查找，而缓存要挑一个"模组列表已经
     * 就绪"的时机去填，反而容易在加载早期取到错的值。
     */
    public static boolean isLoaded() {
        return ModPresence.isLoaded(CURIOS_MODID);
    }

    /**
     * 饰品栏里有没有符合条件的物品。没装 Curios 时一律返回 false。
     */
    public static boolean isEquipped(LivingEntity entity, Predicate<ItemStack> filter) {
        if (!isLoaded()) return false;
        return CuriosInventories.isEquipped(entity, filter);
    }

    /**
     * 饰品栏里的一个位置。
     *
     * 刻意不直接返回 Curios 的 SlotResult：那个类型一旦漏到外面，
     * 外面的代码就跟着碰上了 Curios，本类的隔离也就白做了。
     */
    public record CurioSlotRef(String slotId, int index) {}

    /** 找出饰品栏里第一个符合条件的物品在哪。没装 Curios 时一律为空 */
    public static Optional<CurioSlotRef> findEquipped(LivingEntity entity,
                                                      Predicate<ItemStack> filter) {
        if (!isLoaded()) return Optional.empty();
        return CuriosInventories.findFirst(entity, filter)
                .map(slot -> new CurioSlotRef(slot.slotId(), slot.index()));
    }

    /** 取饰品栏某个位置上的物品。没装 Curios、位置不存在时都返回空堆 */
    public static ItemStack getEquipped(LivingEntity entity, String slotId, int index) {
        if (!isLoaded()) return ItemStack.EMPTY;
        return CuriosInventories.stackAt(entity, slotId, index);
    }

    /**
     * 把物品写回饰品栏。
     *
     * 直接改 getEquipped 拿到的那个 ItemStack 其实也能改到数据，但改完
     * 客户端未必看得见——饰品栏的同步归 Curios 管，它得知道东西变了。
     * 走 setEquippedCurio 就是在通知它。
     */
    public static void setEquipped(LivingEntity entity, String slotId, int index, ItemStack stack) {
        if (!isLoaded()) return;
        CuriosInventories.setEquipped(entity, slotId, index, stack);
    }
}
