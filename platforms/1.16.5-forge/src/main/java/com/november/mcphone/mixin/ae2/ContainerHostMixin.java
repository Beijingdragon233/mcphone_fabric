package com.november.mcphone.mixin.ae2;

import appeng.container.ContainerLocator;
import appeng.core.Api;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.api.features.IWirelessTermHandler;
import com.november.mcphone.feature.terminal.TerminalSlot;
import com.november.mcphone.feature.terminal.integration.ae2.PhoneWirelessHost;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.container.implementations.ContainerTypeBuilder", remap = false)
public abstract class ContainerHostMixin {
    @Shadow(remap = false) private Class<?> hostInterface;

    @Inject(method = "getHostFromPlayerInventory", at = @At("HEAD"), cancellable = true, remap = false)
    private void mcphone$phoneHost(Player player, ContainerLocator locator, CallbackInfoReturnable<Object> callback) {
        if (locator.getItemIndex() != PhoneWirelessHost.PHONE_SLOT) return;
        ItemStack stack = TerminalSlot.get(player);
        IWirelessTermHandler handler = stack.isEmpty() ? null
                : Api.instance().registries().wireless().getWirelessTerminalHandler(stack);
        callback.setReturnValue(handler != null && hostInterface.isAssignableFrom(WirelessTerminalGuiObject.class)
                ? new PhoneWirelessHost(handler, stack, player) : null);
    }
}
