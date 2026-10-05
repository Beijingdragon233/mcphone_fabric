package com.november.mcphone.platform.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;

public final class ItemIcons {
    private ItemIcons() {}

    public static boolean canDraw(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        try {
            BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
            return model != null && !model.isCustomRenderer();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void draw(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.renderItem(stack, x, y);
    }
}
