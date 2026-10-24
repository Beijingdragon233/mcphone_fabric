package com.november.mcphone.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.november.mcphone.MCphone;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * 按键注册。键的身份（翻译键、默认键位）只写在 {@link PhoneKeys} 一处，
 * 这里照 {@link PhoneKeys#ALL} 循环建 {@link KeyMapping} 并注册 ——
 * 加一个键＝PhoneKeys 加一行，这个循环不用动，结构上不可能漏注册。
 *
 * Fabric 没有「冲突上下文」这个概念（NeoForge 的 KeyConflictContext）：
 * 每个键在游戏内处处生效，行为差异由消费方自己判断。
 */
public final class MCphoneKeyBindings {

    /** 按键设置界面里的分类。 */
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MCphone.MODID, "mcphone"));

    private MCphoneKeyBindings() {}

    /** 由 MCphoneClient.onInitializeClient 调用 */
    public static void register() {
        for (PhoneKeys.Key key : PhoneKeys.ALL) {
            KeyMapping mapping = new KeyMapping(
                    key.id(), InputConstants.Type.KEYSYM, key.defaultCode(), CATEGORY);
            key.bind(mapping);
            KeyMappingHelper.registerKeyMapping(mapping);
        }
    }
}
