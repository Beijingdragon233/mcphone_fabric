package com.november.mcphone.mixin.ae2;

import com.november.mcphone.feature.terminal.integration.ae2.PhoneSlotStacks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tfar.ae2wt.terminal.IWirelessTerminalContainer;

/**
 * AE2WTLib（REDUX 4.0.2）五个终端容器的 stillValid（func_75145_c）。
 *
 * <p>原方法只查 host 里的 ItemStack 是否还在背包（引用相等）或 Curios，不含电量/距离
 * （电量与距离在 func_75142_b 单独判定）。手机槽的终端不在背包，原方法返回 false，服务端会
 * 在下一 tick 关菜单。注入点选 RETURN，只把「原返回 false 且 host 仍是手机能力槽那一个对象」
 * 翻成 true，其它返回值不动。
 */
@Pseudo
@Mixin(targets = {
        "tfar.ae2wt.wirelesscraftingterminal.WirelessCraftingTerminalContainer",
        "tfar.ae2wt.wpt.WirelessPatternTerminalContainer",
        "tfar.ae2wt.wirelessinterfaceterminal.WirelessInterfaceTerminalContainer",
        "tfar.ae2wt.wirelessfluidterminal.WirelessFluidTerminalContainer",
        "tfar.ae2wt.wirelesschemicalterminal.WirelessChemicalTerminalContainer"
}, remap = false)
public abstract class Ae2wtlibStillValidMixin {

    @Inject(method = "func_75145_c", at = @At("RETURN"), cancellable = true, remap = false)
    private void mcphone$phoneStillValid(Player player, CallbackInfoReturnable<Boolean> callback) {
        if (!Boolean.FALSE.equals(callback.getReturnValue())) return;
        ItemStack hosted = ((IWirelessTerminalContainer) (Object) this).getTerminalStack();
        if (PhoneSlotStacks.isPhoneHosted(player, hosted)) callback.setReturnValue(Boolean.TRUE);
    }
}