package com.november.mcphone.mixin.refinedstorage;

import com.november.mcphone.core.PhoneItem;
import com.november.mcphone.feature.terminal.TerminalSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.inventory.player.PlayerSlot", remap = false)
public abstract class PlayerSlotMixin {
    @Shadow(remap = false) private int slot;
    @Shadow(remap = false) private String curioSlot;

    @Inject(method = "getStackFromSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void mcphone$phoneTerminal(Player owner, CallbackInfoReturnable<ItemStack> callback) {
        if (slot == Integer.MAX_VALUE && curioSlot == null) {
            callback.setReturnValue(PhoneItem.isCarriedBy(owner) ? TerminalSlot.get(owner) : ItemStack.EMPTY);
        }
    }

    @Inject(method = "getSlotIdInPlayerInventory", at = @At("HEAD"), cancellable = true, remap = false)
    private void mcphone$noInventoryLock(CallbackInfoReturnable<Integer> callback) {
        if (slot == Integer.MAX_VALUE && curioSlot == null) callback.setReturnValue(-1);
    }
}
