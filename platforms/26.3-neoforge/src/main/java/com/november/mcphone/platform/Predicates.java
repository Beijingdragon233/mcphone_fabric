package com.november.mcphone.platform;

import com.november.mcphone.MCphone;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

/** 数据包谓词的查询与判定。 */
public final class Predicates {
    private Predicates() {}

    public static boolean exists(ServerPlayer player, Identifier id) {
        return condition(player, id) != null;
    }

    public static Boolean test(ServerPlayer player, Identifier id) {
        LootItemCondition condition = condition(player, id);
        if (condition == null) return null;
        try {
            LootParams params = new LootParams.Builder((ServerLevel) player.level())
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .create(LootContextParamSets.GIFT);
            LootContext context = new LootContext.Builder(params)
                    .withOptionalRandomSource(RandomSource.create())
                    .create(Optional.empty());
            return condition.test(context);
        } catch (RuntimeException e) {
            MCphone.LOGGER.warn("[MCphone] 谓词判定失败（按判否处理）：{} —— {}", id, e.toString());
            return Boolean.FALSE;
        }
    }

    private static LootItemCondition condition(ServerPlayer player, Identifier id) {
        var access = player.level().getServer().reloadableRegistries().lookup();
        return access.lookupOrThrow(Registries.PREDICATE)
                .get(ResourceKey.create(Registries.PREDICATE, id))
                .map(Holder::value)
                .orElse(null);
    }
}
