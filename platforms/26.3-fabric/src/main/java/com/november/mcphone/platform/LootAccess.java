package com.november.mcphone.platform;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

/** 战利品表的读取与掷取。 */
public final class LootAccess {
    private LootAccess() {}

    public static boolean exists(ServerLevel level, Identifier id) {
        return table(level, id) != LootTable.EMPTY;
    }

    public static List<ItemStack> roll(ServerLevel level, Identifier id, ServerPlayer player) {
        LootTable table = table(level, id);
        if (table == LootTable.EMPTY) return List.of();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.GIFT);
        return List.copyOf(table.getRandomItems(params));
    }

    private static LootTable table(ServerLevel level, Identifier id) {
        return level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
    }
}
