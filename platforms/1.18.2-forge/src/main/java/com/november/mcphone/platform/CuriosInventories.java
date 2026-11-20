package com.november.mcphone.platform;

import com.november.mcphone.compat.CuriosCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Curios 5.0.9 adapter for Minecraft 1.18.2.
 *
 * The 1.20.1 implementation uses CuriosApi.getCuriosInventory(...).resolve().
 * Forge 1.18.2 predates that facade; the helper returns LazyOptional directly.
 * Callers must still check ModPresence before loading this class.
 */
public final class CuriosInventories {

    private CuriosInventories() {
    }

    public static Optional<ICuriosItemHandler> of(LivingEntity entity) {
        return CuriosApi.getCuriosHelper().getCuriosHandler(entity).resolve();
    }

    public static boolean isSupported() {
        return true;
    }

    public static boolean isEquipped(LivingEntity entity, Predicate<ItemStack> filter) {
        return CuriosApi.getCuriosHelper().findFirstCurio(entity, filter).isPresent();
    }

    public static Optional<CuriosCompat.CurioSlotRef> find(LivingEntity entity,
                                                            Predicate<ItemStack> filter) {
        return CuriosApi.getCuriosHelper().findFirstCurio(entity, filter)
                .map(result -> new CuriosCompat.CurioSlotRef(
                        result.slotContext().getIdentifier(),
                        result.slotContext().getIndex()));
    }

    public static ItemStack get(LivingEntity entity, String id, int index) {
        return CuriosApi.getCuriosHelper().findCurio(entity, id, index)
                .map(result -> result.stack())
                .orElse(ItemStack.EMPTY);
    }

    public static void set(LivingEntity entity, String id, int index, ItemStack stack) {
        CuriosApi.getCuriosHelper().setEquippedCurio(entity, id, index, stack);
    }
}