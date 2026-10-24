package com.november.mcphone.feature.terminal.integration;

import com.november.mcphone.MCphone;
import com.november.mcphone.feature.terminal.integration.ae2.Ae2Integration;
import com.november.mcphone.feature.terminal.integration.toms.TomsStorageIntegration;
import com.november.mcphone.platform.ModPresence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 26.2 terminal discovery.
 *
 * <p>AE2 and Tom's Storage have real 26.2 builds. Refined Storage does not, so
 * its integration source is excluded at mount time and this class never links to
 * it. A player with no supported storage mod gets an empty list and the Terminal
 * app remains unavailable.</p>
 */
public final class Terminals {

    private Terminals() {}

    private static volatile List<TerminalIntegration> active = List.of();

    public static void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(Terminals::discover);
    }

    public static void discover() {
        List<TerminalIntegration> candidates = new ArrayList<>(2);

        if (ModPresence.isLoaded(TerminalMods.AE2_MODID)) {
            candidates.add(new Ae2Integration());
        }
        if (ModPresence.isLoaded(TerminalMods.TOMS_STORAGE_MODID)) {
            candidates.add(new TomsStorageIntegration());
        }

        List<TerminalIntegration> ready = new ArrayList<>(candidates.size());
        for (TerminalIntegration integration : candidates) {
            try {
                integration.setup();
                ready.add(integration);
            } catch (Throwable t) {
                MCphone.LOGGER.error("「终端」App 接 {} 失败，已跳过这一家", integration.modId(), t);
            }
        }
        active = List.copyOf(ready);

        if (ready.isEmpty()) {
            MCphone.LOGGER.info("没有支持 26.2 的存储模组，「终端」App 不会出现在主屏上");
        } else {
            MCphone.LOGGER.info("「终端」App 接上了：{}",
                    ready.stream().map(TerminalIntegration::displayName).toList());
        }
    }

    public static List<TerminalIntegration> active() {
        return active;
    }

    public static boolean anyPresent() {
        return !active.isEmpty();
    }

    public static Optional<TerminalIntegration> owner(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        for (TerminalIntegration integration : active) {
            if (integration.claims(stack)) return Optional.of(integration);
        }
        return Optional.empty();
    }

    public static boolean isOpenable(ItemStack stack) {
        return owner(stack).filter(integration -> integration.canOpen(stack)).isPresent();
    }

    public static boolean isInstallable(ItemStack stack) {
        return isOpenable(stack);
    }

    public static boolean isRemoteIncapable(ItemStack stack) {
        return owner(stack).filter(integration -> !integration.canOpen(stack)).isPresent();
    }

    public static boolean anyOpenableIn(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isOpenable(inventory.getItem(slot))) return true;
        }
        return false;
    }
}
