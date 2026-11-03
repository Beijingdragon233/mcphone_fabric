package com.november.mcphone.platform;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/** 药水效果的查询与授予。 */
public final class PlayerEffects {
    private PlayerEffects() {}

    public static boolean available(Identifier effectId) {
        return holder(effectId) != null;
    }

    public static boolean give(ServerPlayer player, Identifier effectId,
                               int durationTicks, int amplifier) {
        Holder<MobEffect> holder = holder(effectId);
        if (holder == null) return false;
        return player.addEffect(new MobEffectInstance(holder, durationTicks, amplifier));
    }

    private static Holder<MobEffect> holder(Identifier id) {
        return BuiltInRegistries.MOB_EFFECT.get(id).orElse(null);
    }
}
