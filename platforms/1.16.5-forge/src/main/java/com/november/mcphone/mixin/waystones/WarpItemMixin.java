package com.november.mcphone.mixin.waystones;

import com.november.mcphone.compat.PhoneWaystoneSelection;
import com.november.mcphone.core.PhoneItem;
import net.blay09.mods.waystones.item.ModItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.blay09.mods.waystones.core.PlayerWaystoneManager", remap = false)
public abstract class WarpItemMixin {
    @Inject(method = "findWarpItem(Lnet/minecraft/entity/Entity;Lnet/minecraft/item/Item;)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void mcphone$phoneWarpItem(Entity entity, Item item, CallbackInfoReturnable<ItemStack> callback) {
        if (item == ModItems.warpStone && entity instanceof Player) {
            Player player = (Player) entity;
            if (player.containerMenu instanceof PhoneWaystoneSelection && PhoneItem.isCarriedBy(player)) {
                callback.setReturnValue(new ItemStack(item));
            }
        }
    }
}
