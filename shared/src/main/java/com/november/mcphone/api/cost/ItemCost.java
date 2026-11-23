package com.november.mcphone.api.cost;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * 从背包里扣物品的代价。用谓词而不是直接认物品种类：有些区别在数据组件上，
 * 比如空白的书与笔和写过字的是同一种物品。多数情况用 {@link ICost#of} 就够。
 */
public final class ItemCost implements ICost {
    private final Predicate<ItemStack> filter;
    private final int count;
    private final Component description;

    public ItemCost(Predicate<ItemStack> filter, int count, Component description) {
        this.filter = filter;
        this.count = count;
        this.description = description;
    }

    public Predicate<ItemStack> filter() { return filter; }

    public int count() { return count; }

    public Component description() { return description; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ItemCost)) return false;
        ItemCost that = (ItemCost) other;
        return java.util.Objects.equals(this.filter, that.filter)
                && this.count == that.count
                && java.util.Objects.equals(this.description, that.description);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(filter);
        hash = 31 * hash + java.util.Objects.hashCode(count);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        return hash;
    }

    @Override public String toString() { return "ItemCost[filter=" + filter + ", count=" + count + ", description=" + description + "]"; }


    @Override
    public boolean canAfford(Player player) {
        if (player.getAbilities().instabuild) return true;
        return countIn(player) >= count;
    }

    /** 先数够不够再动手：扣到一半发现不够会让玩家白白损失前半截物品 */
    @Override
    public boolean consume(Player player) {
        if (player.getAbilities().instabuild) return true;
        if (countIn(player) < count) return false;

        int remaining = count;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !filter.test(stack)) continue;

            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        return true;
    }

    @Override
    public Component describe() {
        return description;
    }

    /** 身上一共有几个符合条件的。遍历全部隔间，盔甲栏与副手也算 */
    private int countIn(Player player) {
        Inventory inventory = player.getInventory();
        int found = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && filter.test(stack)) found += stack.getCount();
        }
        return found;
    }
}
