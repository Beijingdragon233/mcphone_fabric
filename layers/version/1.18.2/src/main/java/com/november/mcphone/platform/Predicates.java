package com.november.mcphone.platform;

import com.november.mcphone.MCphone;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.concurrent.ThreadLocalRandom;

/** Query and evaluate data pack predicates using the 1.18.2 loot context. */
public final class Predicates {

    private Predicates() {
    }

    /** 本服有没有这个谓词。 */
    public static boolean exists(ServerPlayer player, ResourceLocation id) {
        return condition(player, id) != null;
    }

    /**
     * 判定一个数据包谓词。认不得的 id 返回 {@code null}（调用方回"本服没有这个谓词"，
     * 是配置错、不是判否）；求值出错返回 {@code false}（保守：守卫判否），异常不抛进脚本。
     */
    public static Boolean test(ServerPlayer player, ResourceLocation id) {
        LootItemCondition condition = condition(player, id);
        if (condition == null) return null;
        try {
            LootContext context = new LootContext.Builder(player.getLevel())
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withOptionalRandomSeed(ThreadLocalRandom.current().nextLong())
                    .create(LootContextParamSets.GIFT);
            return condition.test(context);
        } catch (RuntimeException e) {
            MCphone.LOGGER.warn("[MCphone] 谓词判定失败（按判否处理）：{} —— {}", id, e.toString());
            return Boolean.FALSE;
        }
    }

    private static LootItemCondition condition(ServerPlayer player, ResourceLocation id) {
        return player.getServer().getPredicateManager().get(id);
    }
}
