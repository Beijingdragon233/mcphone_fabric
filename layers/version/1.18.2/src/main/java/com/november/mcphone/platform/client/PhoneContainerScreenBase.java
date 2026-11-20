package com.november.mcphone.platform.client;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
/** 容器尺寸在构造时确定；新版原版已将这两个字段设为 final。 */
public abstract class PhoneContainerScreenBase<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected PhoneContainerScreenBase(T menu,Inventory inventory,Component title,int w,int h) {
        super(menu,inventory,title); this.imageWidth=w; this.imageHeight=h;
    }

    @Override
    public final void render(com.mojang.blaze3d.vertex.PoseStack pose, int x, int y, float tick) {
        render(new PhoneGraphics(pose), x, y, tick);
    }

    public void render(PhoneGraphics graphics, int x, int y, float tick) {
        super.render(graphics.pose(), x, y, tick);
    }

    @Override
    protected final void renderBg(com.mojang.blaze3d.vertex.PoseStack pose, float tick, int x, int y) {
        renderBg(new PhoneGraphics(pose), tick, x, y);
    }

    protected abstract void renderBg(PhoneGraphics graphics, float tick, int x, int y);

    @Override
    protected final void renderLabels(com.mojang.blaze3d.vertex.PoseStack pose, int x, int y) {
        renderLabels(new PhoneGraphics(pose), x, y);
    }

    protected void renderLabels(PhoneGraphics graphics, int x, int y) {
        super.renderLabels(graphics.pose(), x, y);
    }

    protected void renderTooltip(PhoneGraphics graphics, int x, int y) {
        super.renderTooltip(graphics.pose(), x, y);
    }
}
