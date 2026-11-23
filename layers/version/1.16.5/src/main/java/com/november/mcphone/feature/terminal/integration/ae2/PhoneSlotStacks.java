package com.november.mcphone.feature.terminal.integration.ae2;

import com.november.mcphone.core.PhoneItem;
import com.november.mcphone.feature.terminal.TerminalSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 手机槽 host 的存活判断：容器 host 里那台终端是否仍是手机能力槽里同一个对象（引用相等，
 * 不是 NBT 相等 —— 存储模组要往这个活对象写电量与设置）。
 *
 * <p>只在服务端有意义：{@code stillValid}（{@code PlayerEntity.tick}）只在非客户端调用，
 * 客户端的 host 是打开包反序列化出的副本、与能力槽那份引用必然不同；客户端也不拿它关菜单。
 * 因此这里刻意保持引用相等，不放松成 NBT 比较。
 */
public final class PhoneSlotStacks {

    private PhoneSlotStacks() {}

    public static boolean isPhoneHosted(Player player, ItemStack hosted) {
        return hosted != null && !hosted.isEmpty()
                && TerminalSlot.get(player) == hosted
                && PhoneItem.isCarriedBy(player);
    }
}