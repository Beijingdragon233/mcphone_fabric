package com.november.mcphone.platform;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** 属性修饰符的授予与撤销。 */
public final class PlayerAbilities {
    private PlayerAbilities() {}

    public static boolean available(ServerPlayer player, Identifier attributeId) {
        Holder<Attribute> attribute = attr(attributeId);
        return attribute != null && player.getAttribute(attribute) != null;
    }

    public static boolean grant(ServerPlayer player, Identifier attributeId,
                                Identifier modifierId, double amount, int operation) {
        Holder<Attribute> attribute = attr(attributeId);
        if (attribute == null) return false;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return false;
        instance.addOrUpdateTransientModifier(
                new AttributeModifier(modifierId, amount, op(operation)));
        return true;
    }

    public static boolean revoke(ServerPlayer player, Identifier attributeId, Identifier modifierId) {
        Holder<Attribute> attribute = attr(attributeId);
        if (attribute == null) return false;
        AttributeInstance instance = player.getAttribute(attribute);
        return instance != null && instance.removeModifier(modifierId);
    }

    private static Holder<Attribute> attr(Identifier id) {
        return BuiltInRegistries.ATTRIBUTE.get(id).orElse(null);
    }

    private static AttributeModifier.Operation op(int operation) {
        return switch (operation) {
            case 1 -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case 2 -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }
}
