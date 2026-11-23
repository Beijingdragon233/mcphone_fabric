package com.november.mcphone.feature.terminal.integration.ae2;

import appeng.container.ContainerLocator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import tfar.ae2wt.terminal.AbstractWirelessTerminalItem;
import tfar.ae2wt.util.TerminalOpener;

/**
 * AE2WTLib（REDUX 4.0.2）—— 唯一引用 {@code tfar.ae2wt} 的类。签名不含 tfar 类型，
 * 调用方先按 modid 判断加载，未安装时本类不会被加载。
 *
 * <p>手机槽用 {@link TerminalOpener#openFromStack}：它按传入的 ItemStack 直接造 host（保留活
 * 对象），定位器退回 curios（index = -1），五个容器都对 -1 跳过背包加锁。不能用
 * {@code item.open(player, locator)}：那条路经 {@code ContainerHelper.resolveTerminal} 的
 * {@code 0..getContainerSize()} 范围检查，手机槽的越大整数返回空、静默不开。
 *
 * <p>{@code openFromStack} 是 void，且对「通用终端无有效模式」「化学终端但缺化学模组」等
 * 情况会静默不开。{@code NetworkHooks.openGui} 在服务端同步换掉 {@code player.containerMenu}，
 * 所以用调用前后的菜单对象是否变化来判断真的开了，而不是无条件报成功。
 */
final class Ae2wtlibSupport {

    private Ae2wtlibSupport() {}

    static boolean isTerminal(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof AbstractWirelessTerminalItem;
    }

    /** 打开手机槽那一台；返回是否真的换上了新菜单。{@code modeHint} 传 null 表示开当前模式。 */
    static boolean openFromPhone(ServerPlayer player, ItemStack stack) {
        AbstractContainerMenu before = player.containerMenu;
        TerminalOpener.openFromStack(player, stack, null);
        return opened(player, before);
    }

    /** 背包那一台交给物品自己的 open(locator)；返回是否真的换上了新菜单。 */
    static boolean openFromInventory(ServerPlayer player, ItemStack stack, ContainerLocator locator) {
        AbstractContainerMenu before = player.containerMenu;
        ((AbstractWirelessTerminalItem) stack.getItem()).open(player, locator);
        return opened(player, before);
    }

    private static boolean opened(ServerPlayer player, AbstractContainerMenu before) {
        AbstractContainerMenu after = player.containerMenu;
        return after != before && after != player.inventoryMenu;
    }
}