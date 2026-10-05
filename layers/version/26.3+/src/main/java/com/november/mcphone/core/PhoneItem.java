package com.november.mcphone.core;

import com.november.mcphone.MCphone;
import com.november.mcphone.compat.CuriosCompat;
import com.november.mcphone.core.client.PhoneScreenOpener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** 26.3 的设备物品；Item.use 已从结果栈改为直接返回 InteractionResult。 */
public class PhoneItem extends Item {

    private final DeviceKind kind;

    public PhoneItem(Properties properties, DeviceKind kind) { super(properties); this.kind = kind; }
    public DeviceKind kind() { return kind; }
    public static boolean isDevice(ItemStack stack) { return stack.getItem() instanceof PhoneItem; }
    public static @Nullable DeviceKind kindOf(ItemStack stack) {
        return stack.getItem() instanceof PhoneItem item ? item.kind() : null;
    }
    public static boolean isCarriedBy(Player player) {
        if (player.getInventory().contains(PhoneItem::isDevice)) return true;
        return CuriosCompat.isEquipped(player, PhoneItem::isDevice);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) PhoneScreenOpener.open(new PhoneLocation.InHand(hand));
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        String deviceName = PhoneItemData.getDeviceName(stack);
        return deviceName != null && !deviceName.isBlank()
                ? Component.literal(deviceName) : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        builder.accept(Component.translatable(kind.openTooltipKey()));
        builder.accept(Component.translatable("mcphone.item.tooltip.version", MCphone.getVersion()));
    }
}
