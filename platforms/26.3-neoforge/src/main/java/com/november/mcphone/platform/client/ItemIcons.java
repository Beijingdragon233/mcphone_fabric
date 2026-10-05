package com.november.mcphone.platform.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ItemIcons {
    private ItemIcons() {}

    public static boolean canDraw(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        try {
            ItemModelResolver resolver = mc.getItemModelResolver();
            TrackingItemStackRenderState state = new TrackingItemStackRenderState();
            resolver.updateForTopItem(state, stack, ItemDisplayContext.GUI, mc.level, mc.player, 0);
            Object identity = state.getModelIdentity();
            if (identity instanceof Iterable<?> elements) {
                for (Object element : elements) {
                    if (element instanceof SpecialModelWrapper<?>) return false;
                }
            }
            return !state.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void draw(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        graphics.item(stack, x, y);
    }
}
