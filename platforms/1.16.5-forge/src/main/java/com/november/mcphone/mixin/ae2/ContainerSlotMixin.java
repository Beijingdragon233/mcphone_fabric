package com.november.mcphone.mixin.ae2;

import appeng.api.implementations.guiobjects.IGuiItemObject;
import com.november.mcphone.feature.terminal.integration.ae2.PhoneWirelessHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.container.AEBaseContainer", remap = false)
public abstract class ContainerSlotMixin {
    @Inject(method = "lockPlayerInventorySlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void mcphone$noPhoneInventoryLock(int slot, CallbackInfo callback) {
        // The phone capability is outside the inventory. AE2 otherwise rejects the marker
        // during menu construction, before ensureGuiItemIsInSlot can check the live host.
        if (slot == PhoneWirelessHost.PHONE_SLOT) callback.cancel();
    }

    @Inject(method = "ensureGuiItemIsInSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void mcphone$installedTerminal(IGuiItemObject host, int slot, CallbackInfoReturnable<Boolean> callback) {
        if (slot == PhoneWirelessHost.PHONE_SLOT && host instanceof PhoneWirelessHost) {
            callback.setReturnValue(((PhoneWirelessHost) host).stillInstalled());
        }
    }
}
