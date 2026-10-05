package com.november.mcphone.platform.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 容器界面（末影箱、唱片仓、终端卡槽）的每支一份基类 —— 把 26.x 换掉的【那几个渲染入口】
 * 挡在共用代码之外，让子类在四支上都覆写同一组名字。
 *
 * <h2>这一支上它只剩一个构造</h2>
 *
 * {@code render}、{@code renderBg}、{@code renderLabels}、{@code renderTooltip} 在这一支
 * 全是 {@link AbstractContainerScreen} 自己的方法，而且原版那条链早把它们串好了：
 * {@code render} 叫 {@code renderBackground}，那一句是「透明底那一层 ＋ {@code renderBg}」，
 * {@code renderBackground} 又叫 {@code renderBg}。所以这一层【一个方法都不覆写】——
 * 子类覆写的就是原版那几个，中间插一层只会多一次转发。
 *
 * <p>要它的原因只有一条：26.x 把 {@code imageWidth} / {@code imageHeight} 改成了
 * {@code protected final}，尺寸只能从构造进去，共用代码于是需要一个【四支同签名】的构造。
 * 这一支上这两个字段还可写，就先 super 再赋值 —— 与本文件改动前子类自己那两行一模一样。
 *
 * <p>另一个差别也在这儿说一句：原版那个三参构造会顺手算一次 {@code inventoryLabelY}，用的是
 * 当时的默认高度；26.x 的五参构造用【真实尺寸】算同一式子。本仓三个容器界面都在构造里自己写了
 * {@code inventoryLabelX/Y}，所以这一支上「先 super 再覆盖尺寸」与「把尺寸递进构造」
 * 落到最后的值相同。
 *
 * <p>【这一支为什么不写行号】1.21.1 那两份把 {@code :41}/{@code :45}/{@code :106}/{@code :114}
 * 写进了注释，那是对着 1.21.1 的官方源逐行量出来的。1.20.1 这一支本仓没有可查的官方源，
 * 于是这里一个行号都不写：那三件事（三参构造在、两个字段可写、赋值不撞 final）由本支的
 * {@code :compileJava} 编过证明，见 docs/PORTING-26.3.md §三十五。
 *
 * <p>26.3 那一份为什么有九十个方法行，看它自己。
 */
public abstract class PhoneContainerScreenBase<T extends AbstractContainerMenu>
        extends AbstractContainerScreen<T> {

    protected PhoneContainerScreenBase(T menu, Inventory playerInventory, Component title,
                                       int imageWidth, int imageHeight) {
        super(menu, playerInventory, title);
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }
}
