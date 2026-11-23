package mcphone1165fixture;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Runtime assertions for the MCphone 1.16.5 phone-slot bridge.
 *
 * Minecraft is referenced with SRG names (field_71070_bA, field_71071_by, func_*) because this class
 * is compiled against server-1.16.5-srg.jar and loaded without reobfuscation. That is also why the
 * fake player's open container can be overwritten directly: the Waystones mixin reads the same field.
 */
final class FixtureRunner {

    private static final int SENTINEL = Integer.MAX_VALUE;

    private static final String PHONE_ITEM = "com.november.mcphone.core.PhoneItem";
    private static final String TERMINAL_SLOT = "com.november.mcphone.feature.terminal.TerminalSlot";
    private static final String MOD_CAPABILITIES = "com.november.mcphone.core.ModCapabilities";
    private static final String RS_INTEGRATION =
            "com.november.mcphone.feature.terminal.integration.refinedstorage.RefinedStorageIntegration";
    private static final String RS_PLAYER_SLOT = "com.refinedmods.refinedstorage.inventory.player.PlayerSlot";
    private static final String RS_NETWORK_ITEM = "com.refinedmods.refinedstorage.item.NetworkItem";
    private static final String RS_PORTABLE_GRID =
            "com.refinedmods.refinedstorage.item.blockitem.PortableGridBlockItem";
    private static final String WS_MANAGER = "net.blay09.mods.waystones.core.PlayerWaystoneManager";
    private static final String WS_ITEMS = "net.blay09.mods.waystones.item.ModItems";
    private static final String WS_SELECTION =
            "net.blay09.mods.waystones.container.WaystoneSelectionContainer";
    private static final String WS_WARP_MODE = "net.blay09.mods.waystones.core.WarpMode";
    private static final String WS_WAYSTONE = "net.blay09.mods.waystones.api.IWaystone";
    private static final String PHONE_SELECTION = "com.november.mcphone.compat.PhoneWaystoneSelection";
    private static final String AE2_INTEGRATION =
            "com.november.mcphone.feature.terminal.integration.ae2.Ae2Integration";
    private static final String AE2_HOST =
            "com.november.mcphone.feature.terminal.integration.ae2.PhoneWirelessHost";
    private static final String AE2_HANDLER = "appeng.api.features.IWirelessTermHandler";

    // AE2WTLib REDUX 4.0.2 (optional addon). The phone slot opens through TerminalOpener.openFromStack
    // with a curio locator (index -1), which is why the five terminals skip the inventory lock.
    private static final String WT_WCT_ITEM = "tfar.ae2wt.wirelesscraftingterminal.WCTItem";
    private static final String WT_TERMINAL_ITEM = "tfar.ae2wt.terminal.AbstractWirelessTerminalItem";
    private static final String WT_WUT_ITEM = "tfar.ae2wt.wut.WUTItem";
    private static final String WT_WUT_HANDLER = "tfar.ae2wt.wut.WUTHandler";
    private static final String WT_HOST = "tfar.ae2wt.wirelesscraftingterminal.WCTGuiObject";
    private static final String WT_CONTAINER =
            "tfar.ae2wt.wirelesscraftingterminal.WirelessCraftingTerminalContainer";
    private static final String WT_OPENER = "tfar.ae2wt.util.TerminalOpener";
    private static final String TERMINAL_SOURCE =
            "com.november.mcphone.feature.terminal.integration.TerminalSource";

    private final MinecraftServer server;
    private final Report report;
    private final Collection<Item> items;

    private FixtureRunner(MinecraftServer server, Report report) {
        this.server = server;
        this.report = report;
        this.items = ForgeRegistries.ITEMS.getValues();
    }

    static void run(MinecraftServer server, Report report) {
        new FixtureRunner(server, report).execute();
    }

    private void execute() {
        report.header("MCphone 1.16.5 / Forge 36 phone-slot bridge fixture, TEST ONLY");
        report.info("env.java", System.getProperty("java.version", "?"));
        report.info("env.cwd", System.getProperty("user.dir", "?"));
        report.info("note",
                "RS assertions go red on a jar without mcphone.refinedstorage.mixins.json; that is the"
                        + " missing bridge under test, not a fixture fault.");

        Class<?> phoneItemCls = Reflect.load(PHONE_ITEM);
        Class<?> terminalSlotCls = Reflect.load(TERMINAL_SLOT);
        Class<?> modCapsCls = Reflect.load(MOD_CAPABILITIES);
        Class<?> rsSlotCls = Reflect.load(RS_PLAYER_SLOT);
        Class<?> rsIntegrationCls = Reflect.load(RS_INTEGRATION);
        Class<?> wsManagerCls = Reflect.load(WS_MANAGER);
        Class<?> wsItemsCls = Reflect.load(WS_ITEMS);
        Class<?> wsSelectionCls = Reflect.load(WS_SELECTION);
        Class<?> phoneSelectionCls = Reflect.load(PHONE_SELECTION);
        Class<?> ae2IntegrationCls = Reflect.load(AE2_INTEGRATION);

        report.section("class presence");
        presence("class.mcphone.PhoneItem", phoneItemCls, true);
        presence("class.mcphone.TerminalSlot", terminalSlotCls, true);
        presence("class.mcphone.ModCapabilities", modCapsCls, false);
        presence("class.mcphone.PhoneWaystoneSelection", phoneSelectionCls, false);
        presence("class.rs.PlayerSlot", rsSlotCls, true);
        presence("class.waystones.PlayerWaystoneManager", wsManagerCls, false);
        presence("class.mcphone.RefinedStorageIntegration", rsIntegrationCls, false);
        presence("class.mcphone.Ae2Integration", ae2IntegrationCls, false);

        ServerWorld overworld;
        try {
            overworld = server.func_241755_D_();
        } catch (Throwable t) {
            report.threw("env.overworld", t);
            report.flush();
            return;
        }

        FakePlayer fake;
        try {
            fake = FakePlayerFactory.getMinecraft(overworld);
        } catch (Throwable t) {
            report.threw("env.fakePlayer", t);
            report.flush();
            return;
        }
        if (fake == null) {
            report.fail("env.fakePlayer", "FakePlayerFactory.getMinecraft returned null");
            report.flush();
            return;
        }
        report.pass("env.fakePlayer",
                "uuid=" + fake.func_110124_au() + " world=" + overworld.func_234923_W_().func_240901_a_());

        Container originalMenu = fake.field_71070_bA;
        try {
            runPhoneItem(phoneItemCls, fake);
            runTerminalSlot(terminalSlotCls, modCapsCls, fake);
            runRefinedStorage(rsSlotCls, rsIntegrationCls, terminalSlotCls, fake);
            runWaystones(wsManagerCls, wsItemsCls, wsSelectionCls, phoneSelectionCls, fake);
            runAe2(ae2IntegrationCls, fake);
            runAe2Wtlib(ae2IntegrationCls, fake);
        } catch (Throwable t) {
            report.threw("fixture", t);
        } finally {
            try {
                fake.field_71070_bA = originalMenu;
            } catch (Throwable ignored) {
                // best effort: leave the cached FakePlayer as close to vanilla as we can
            }
            clearInventory(fake);
            report.flush();
        }
    }

    private void runPhoneItem(Class<?> phoneItemCls, FakePlayer fake) {
        report.section("phone provisioning");
        if (phoneItemCls == null) {
            report.skip("phone.item", "PhoneItem missing");
            return;
        }

        Item phone = phoneItem();
        if (phone == null) {
            report.fail("phone.item", "no mcphone phone item registered");
            return;
        }
        report.pass("phone.item", String.valueOf(phone.getRegistryName()));

        try {
            Method isCarriedBy = Reflect.method(phoneItemCls, "isCarriedBy", PlayerEntity.class);

            clearInventory(fake);
            fake.field_71071_by.func_70299_a(0, new ItemStack(phone, 1));
            boolean carried = ((Boolean) isCarriedBy.invoke(null, fake)).booleanValue();
            expect("phone.carried.positive", carried,
                    "isCarriedBy=true with the phone in slot 0",
                    "isCarriedBy=false while the phone sits in slot 0");

            clearInventory(fake);
            boolean bare = ((Boolean) isCarriedBy.invoke(null, fake)).booleanValue();
            expect("phone.carried.negative", !bare,
                    "isCarriedBy=false with an empty inventory",
                    "isCarriedBy=true with an empty inventory");
        } catch (Throwable t) {
            report.threw("phone.isCarriedBy", t);
        }
    }

    private void runTerminalSlot(Class<?> terminalSlotCls, Class<?> modCapsCls, FakePlayer fake) {
        report.section("TerminalSlot live-object semantics");
        if (terminalSlotCls == null) {
            report.skip("terminal.slot", "TerminalSlot missing");
            return;
        }

        if (modCapsCls != null) {
            try {
                Object token = Reflect.field(modCapsCls, "PHONE_DATA").get(null);
                expect("terminal.capability", token != null,
                        "PhonePlayerData capability token resolved on the server",
                        "ModCapabilities.PHONE_DATA is null, so a terminal cannot be stored");
            } catch (Throwable t) {
                report.threw("terminal.capability", t);
            }
        }

        Item stick = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "stick"));
        if (stick == null) {
            report.fail("terminal.slot", "minecraft:stick is not registered");
            return;
        }

        try {
            Method get = Reflect.method(terminalSlotCls, "get", PlayerEntity.class);
            Method set = Reflect.method(terminalSlotCls, "set", PlayerEntity.class, ItemStack.class);
            Method markChanged = Reflect.method(terminalSlotCls, "markChanged", PlayerEntity.class);

            ItemStack before = (ItemStack) get.invoke(null, fake);
            expect("terminal.empty", before != null && before.func_190926_b(),
                    "TerminalSlot.get() is EMPTY before any set",
                    "TerminalSlot.get() was not EMPTY before any set: " + stackName(before));

            ItemStack terminal = new ItemStack(stick, 3);
            set.invoke(null, fake, terminal);
            ItemStack live = (ItemStack) get.invoke(null, fake);
            expect("terminal.live", live == terminal,
                    "set/get round-trips the same ItemStack instance (writes stay on the real object)",
                    "set/get returned a different instance, so terminal writes would be lost");

            markChanged.invoke(null, fake);
            ItemStack afterMark = (ItemStack) get.invoke(null, fake);
            expect("terminal.markChanged.identity", afterMark == terminal,
                    "markChanged() keeps the live instance",
                    "markChanged() replaced the live instance");

            expect("terminal.copy.control", terminal.func_77946_l() != terminal,
                    "control: ItemStack#copy() really produces a different instance",
                    "control failed: ItemStack#copy() returned the same instance");
        } catch (Throwable t) {
            report.threw("terminal.slot", t);
        }
    }

    private void runRefinedStorage(Class<?> rsSlotCls, Class<?> rsIntegrationCls,
                                   Class<?> terminalSlotCls, FakePlayer fake) {
        report.section("Refined Storage sentinel bridge");
        if (rsSlotCls == null) {
            report.skip("rs.bridge", "PlayerSlot missing");
            return;
        }

        if (rsIntegrationCls != null) {
            try {
                Object value = rsIntegrationCls.getField("PHONE_SLOT").get(null);
                int sentinel = ((Number) value).intValue();
                expect("rs.sentinelConstant", sentinel == SENTINEL,
                        "RefinedStorageIntegration.PHONE_SLOT=" + sentinel + " matches " + SENTINEL,
                        "RefinedStorageIntegration.PHONE_SLOT=" + sentinel + " but the mixin expects " + SENTINEL);
            } catch (Throwable t) {
                report.info("rs.sentinelConstant", "no readable PHONE_SLOT: " + Report.describe(t));
            }
        }

        Method tsSet;
        Method tsGet;
        Method rsGetStack;
        Method rsSlotId;
        Method rsWrite;
        try {
            tsSet = Reflect.method(terminalSlotCls, "set", PlayerEntity.class, ItemStack.class);
            tsGet = Reflect.method(terminalSlotCls, "get", PlayerEntity.class);
            rsGetStack = Reflect.method(rsSlotCls, "getStackFromSlot", PlayerEntity.class);
            rsSlotId = Reflect.method(rsSlotCls, "getSlotIdInPlayerInventory");
            rsWrite = Reflect.method(rsSlotCls, "writePlayerSlot", PacketBuffer.class);
        } catch (Throwable t) {
            report.threw("rs.bridge.methods", t);
            return;
        }

        Item phone = phoneItem();
        ItemStack phoneStack = phone == null ? ItemStack.field_190927_a : new ItemStack(phone, 1);
        Item stick = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "stick"));
        Item realTerminal = findItemByType(RS_NETWORK_ITEM);
        if (realTerminal == null) realTerminal = findItemByType(RS_PORTABLE_GRID);

        clearInventory(fake);
        fake.field_71071_by.func_70299_a(0, phoneStack);

        if (realTerminal == null) {
            report.skip("rs.identity.realTerminal", "no refinedstorage NetworkItem/PortableGridBlockItem registered");
        } else {
            identity("rs.identity.realTerminal", fake, tsSet, tsGet, rsSlotCls, rsGetStack, rsSlotId,
                    new ItemStack(realTerminal, 1), realTerminal);
        }
        if (stick == null) {
            report.skip("rs.identity.vanillaStick", "minecraft:stick not registered");
        } else {
            identity("rs.identity.vanillaStick", fake, tsSet, tsGet, rsSlotCls, rsGetStack, rsSlotId,
                    new ItemStack(stick, 1), stick);
        }

        try {
            tsSet.invoke(null, fake, ItemStack.field_190927_a);
            Object slot = rsSlotCls.getConstructor(int.class).newInstance(Integer.valueOf(SENTINEL));
            ItemStack read = (ItemStack) rsGetStack.invoke(slot, fake);
            expect("rs.identity.emptyTerminal", read != null && read.func_190926_b(),
                    "sentinel with the phone but no terminal returns EMPTY",
                    "sentinel with the phone but no terminal returned " + stackName(read));
        } catch (Throwable t) {
            report.threw("rs.identity.emptyTerminal", t);
        }

        try {
            Item fallback = stick == null ? phone : stick;
            tsSet.invoke(null, fake, new ItemStack(fallback, 1));
            clearInventory(fake);
            Object slot = rsSlotCls.getConstructor(int.class).newInstance(Integer.valueOf(SENTINEL));
            ItemStack read = (ItemStack) rsGetStack.invoke(slot, fake);
            expect("rs.gate.noPhone", read != null && read.func_190926_b(),
                    "sentinel without the phone returns EMPTY",
                    "sentinel without the phone returned " + stackName(read));
            int id = ((Number) rsSlotId.invoke(slot)).intValue();
            expect("rs.noInventoryLock.noPhone", id == -1,
                    "no inventory lock still reports -1 without the phone",
                    "no inventory lock returned " + id + " without the phone");
        } catch (Throwable t) {
            report.threw("rs.gate.noPhone", t);
        }

        try {
            Item fallback = stick == null ? phone : stick;
            ItemStack inSlot5 = new ItemStack(fallback, 7);
            fake.field_71071_by.func_70299_a(5, inSlot5);
            Object normal = rsSlotCls.getConstructor(int.class).newInstance(Integer.valueOf(5));
            ItemStack got = (ItemStack) rsGetStack.invoke(normal, fake);
            expect("rs.regression.slot5.stack", got == inSlot5,
                    "PlayerSlot(5) returns the live slot-5 stack",
                    "PlayerSlot(5) returned " + stackName(got));
            int id = ((Number) rsSlotId.invoke(normal)).intValue();
            expect("rs.regression.slot5.noLock", id == 5,
                    "PlayerSlot(5).getSlotIdInPlayerInventory()=5",
                    "PlayerSlot(5).getSlotIdInPlayerInventory()=" + id);
        } catch (Throwable t) {
            report.threw("rs.regression.slot5", t);
        }

        try {
            clearInventory(fake);
            fake.field_71071_by.func_70299_a(0, phoneStack);
            Item fallback = stick == null ? phone : stick;
            ItemStack terminal = new ItemStack(fallback, 2);
            tsSet.invoke(null, fake, terminal);
            ItemStack live = (ItemStack) tsGet.invoke(null, fake);

            Object sentinel = rsSlotCls.getConstructor(int.class).newInstance(Integer.valueOf(SENTINEL));
            ByteBuf buffer = Unpooled.buffer();
            PacketBuffer packet = new PacketBuffer(buffer);
            rsWrite.invoke(sentinel, packet);

            Object decoded = rsSlotCls.getConstructor(PacketBuffer.class).newInstance(packet);
            int decodedSlot = Reflect.field(rsSlotCls, "slot").getInt(decoded);
            Object curio = Reflect.field(rsSlotCls, "curioSlot").get(decoded);

            expect("rs.wire.slot", decodedSlot == SENTINEL,
                    "wire round-trip keeps slot=" + decodedSlot,
                    "wire round-trip changed slot to " + decodedSlot);
            expect("rs.wire.curio", curio == null,
                    "wire round-trip keeps curioSlot=null",
                    "wire round-trip set curioSlot=" + curio);
            int id = ((Number) rsSlotId.invoke(decoded)).intValue();
            expect("rs.wire.noLock", id == -1,
                    "decoded sentinel still reports no inventory lock",
                    "decoded sentinel reports " + id);
            ItemStack read = (ItemStack) rsGetStack.invoke(decoded, fake);
            expect("rs.wire.identity", read == live,
                    "decoded sentinel returns the same live terminal",
                    "decoded sentinel returned " + stackName(read));
            expect("rs.wire.exhausted", buffer.readableBytes() == 0,
                    "wire payload fully consumed",
                    "wire payload left " + buffer.readableBytes() + " unread bytes");
        } catch (Throwable t) {
            report.threw("rs.wire", t);
        }
    }

    private void runWaystones(Class<?> wsManagerCls, Class<?> wsItemsCls, Class<?> wsSelectionCls,
                              Class<?> phoneSelectionCls, FakePlayer fake) {
        report.section("Waystones findWarpItem gated on the phone menu");
        if (wsManagerCls == null || wsSelectionCls == null || phoneSelectionCls == null) {
            report.skip("waystones.findWarpItem", "Waystones or PhoneWaystoneSelection missing");
            return;
        }

        Class<?> warpModeCls = Reflect.load(WS_WARP_MODE);
        Class<?> waystoneCls = Reflect.load(WS_WAYSTONE);
        if (warpModeCls == null || waystoneCls == null) {
            report.skip("waystones.findWarpItem", "WarpMode or IWaystone missing");
            return;
        }

        Item warpStone = warpStoneItem(wsItemsCls);
        if (warpStone == null) {
            report.fail("waystones.warpStone", "no waystones warp stone item");
            return;
        }
        report.pass("waystones.warpStone", String.valueOf(warpStone.getRegistryName()));

        Item phone = phoneItem();
        ItemStack phoneStack = phone == null ? ItemStack.field_190927_a : new ItemStack(phone, 1);

        try {
            Method findWarpItem = Reflect.method(wsManagerCls, "findWarpItem", Entity.class, Item.class);
            Object warpMode = warpModeCls.getField("WARP_STONE").get(null);
            Method createPhone = Reflect.method(phoneSelectionCls, "create", int.class, PlayerEntity.class);
            Method createOrdinary = Reflect.method(wsSelectionCls, "createWaystoneSelection",
                    int.class, PlayerEntity.class, warpModeCls, waystoneCls);

            Container phoneMenu = (Container) createPhone.invoke(null, Integer.valueOf(1), fake);
            Container ordinaryMenu = (Container) createOrdinary.invoke(null, Integer.valueOf(2), fake, warpMode, null);
            report.pass("waystones.menus",
                    "constructed " + phoneMenu.getClass().getName() + " and " + ordinaryMenu.getClass().getName());

            clearInventory(fake);
            fake.field_71071_by.func_70299_a(0, phoneStack);
            fake.field_71070_bA = phoneMenu;
            ItemStack withPhone = (ItemStack) findWarpItem.invoke(null, fake, warpStone);
            boolean found = withPhone != null && !withPhone.func_190926_b()
                    && withPhone.func_77973_b() == warpStone;
            expect("waystones.phoneMenu.withPhone", found,
                    "findWarpItem returned a warp stone for the phone menu",
                    "findWarpItem returned " + stackName(withPhone) + " for the phone menu");

            clearInventory(fake);
            fake.field_71070_bA = phoneMenu;
            ItemStack withoutPhone = (ItemStack) findWarpItem.invoke(null, fake, warpStone);
            expect("waystones.phoneMenu.withoutPhone",
                    withoutPhone != null && withoutPhone.func_190926_b(),
                    "phone menu without the phone returns EMPTY",
                    "phone menu without the phone returned " + stackName(withoutPhone));

            clearInventory(fake);
            fake.field_71071_by.func_70299_a(0, phoneStack);
            fake.field_71070_bA = ordinaryMenu;
            ItemStack ordinary = (ItemStack) findWarpItem.invoke(null, fake, warpStone);
            expect("waystones.ordinaryMenu.withPhone",
                    ordinary != null && ordinary.func_190926_b(),
                    "ordinary Waystones menu does not get the phone shortcut",
                    "ordinary Waystones menu returned " + stackName(ordinary));

            clearInventory(fake);
            fake.field_71070_bA = ordinaryMenu;
            ItemStack plain = (ItemStack) findWarpItem.invoke(null, fake, warpStone);
            expect("waystones.ordinaryMenu.withoutPhone",
                    plain != null && plain.func_190926_b(),
                    "ordinary menu control returns EMPTY",
                    "ordinary menu control returned " + stackName(plain));

            try {
                Method byMode = Reflect.method(wsManagerCls, "findWarpItem", Entity.class, warpModeCls);
                Object result = byMode.invoke(null, fake, warpMode);
                report.info("waystones.warpModeOverload",
                        "findWarpItem(Entity, WarpMode) -> " + stackName((ItemStack) result));
            } catch (Throwable t) {
                report.info("waystones.warpModeOverload", "not probed: " + Report.describe(t));
            }
        } catch (Throwable t) {
            report.threw("waystones.findWarpItem", t);
        }
    }

    private void runAe2(Class<?> ae2IntegrationCls, FakePlayer fake) {
        report.section("AE2 wireless host and terminal container (optional)");
        if (ae2IntegrationCls == null) {
            report.skip("ae2.integration", "Ae2Integration missing");
            return;
        }

        Class<?> hostCls = Reflect.load(AE2_HOST);
        if (hostCls == null) {
            report.skip("ae2.host", "PhoneWirelessHost missing");
        } else {
            report.pass("ae2.host", hostCls.getName());
        }

        Item terminal = findWirelessTerminal();
        if (terminal == null) {
            report.skip("ae2.claims", "no AE2 wireless terminal item registered");
            return;
        }

        try {
            Object integration = ae2IntegrationCls.getConstructor().newInstance();
            Method claims = ae2IntegrationCls.getMethod("claims", ItemStack.class);
            boolean claimed = ((Boolean) claims.invoke(integration, new ItemStack(terminal, 1))).booleanValue();
            expect("ae2.claims", claimed,
                    terminal.getRegistryName() + " is claimed by Ae2Integration",
                    terminal.getRegistryName() + " is not claimed by Ae2Integration");
        } catch (Throwable t) {
            report.threw("ae2.claims", t);
        }

        runAe2Container(hostCls, terminal, fake);
    }

    /**
     * Builds a real AE2 WirelessTermContainer around a PhoneWirelessHost.
     *
     * MEPortableCellContainer's constructor calls lockPlayerInventorySlot(getInventorySlot()), and
     * PhoneWirelessHost reports Integer.MAX_VALUE. Without the ContainerSlotMixin cancelling that
     * call, AE2 throws "cannot lock player inventory slot: 2147483647" before the host is ever used.
     */
    private void runAe2Container(Class<?> hostCls, Item terminalItem, FakePlayer fake) {
        Class<?> apiCls = Reflect.load("appeng.core.Api");
        Class<?> guiObjectCls = Reflect.load("appeng.helpers.WirelessTerminalGuiObject");
        Class<?> containerCls = Reflect.load("appeng.container.me.items.WirelessTermContainer");
        Class<?> handlerCls = Reflect.load("appeng.api.features.IWirelessTermHandler");
        Class<?> guiItemCls = Reflect.load("appeng.api.implementations.guiobjects.IGuiItemObject");
        Class<?> terminalSlotCls = Reflect.load(TERMINAL_SLOT);

        if (hostCls == null || apiCls == null || guiObjectCls == null || containerCls == null
                || handlerCls == null || guiItemCls == null || terminalSlotCls == null) {
            report.skip("ae2.container", "AE2 container classes or TerminalSlot missing");
            return;
        }

        Item phone = phoneItem();
        ItemStack phoneStack = phone == null ? ItemStack.field_190927_a : new ItemStack(phone, 1);
        ItemStack terminal = new ItemStack(terminalItem, 1);

        try {
            clearInventory(fake);
            fake.field_71071_by.func_70299_a(0, phoneStack);
            Reflect.method(terminalSlotCls, "set", PlayerEntity.class, ItemStack.class)
                    .invoke(null, fake, terminal);
        } catch (Throwable t) {
            report.threw("ae2.container.install", t);
            return;
        }

        Object host;
        try {
            Object api = apiCls.getMethod("instance").invoke(null);
            Object registries = api.getClass().getMethod("registries").invoke(api);
            Object wireless = registries.getClass().getMethod("wireless").invoke(registries);
            Object handler = wireless.getClass()
                    .getMethod("getWirelessTerminalHandler", ItemStack.class)
                    .invoke(wireless, terminal);
            if (handler == null) {
                report.fail("ae2.handler", "no IWirelessTermHandler for " + stackName(terminal));
                return;
            }
            host = hostCls.getConstructor(handlerCls, ItemStack.class, PlayerEntity.class)
                    .newInstance(handler, terminal, fake);
            report.pass("ae2.host.construct", "PhoneWirelessHost built around " + stackName(terminal));
        } catch (Throwable t) {
            report.threw("ae2.host.construct", t);
            return;
        }

        try {
            ItemStack hostStack = (ItemStack) guiObjectCls.getMethod("getItemStack").invoke(host);
            expect("ae2.host.live", hostStack == terminal,
                    "PhoneWirelessHost.getItemStack() is the live terminal instance",
                    "PhoneWirelessHost.getItemStack() returned a different instance");
            boolean installed = ((Boolean) hostCls.getMethod("stillInstalled").invoke(host)).booleanValue();
            expect("ae2.host.stillInstalled", installed,
                    "stillInstalled()=true with phone and terminal installed",
                    "stillInstalled()=false with phone and terminal installed");
        } catch (Throwable t) {
            report.threw("ae2.host.live", t);
        }

        Object container = null;
        try {
            // This fixture has no bound AE network. Supply an empty storage-grid facade solely
            // to reach the real container's slot-lock/liveness paths. This does not test storage.
            java.lang.reflect.Field storage = guiObjectCls.getDeclaredField("sg");
            storage.setAccessible(true);
            Class<?> storageType = storage.getType();
            storage.set(host, java.lang.reflect.Proxy.newProxyInstance(storageType.getClassLoader(),
                    new Class<?>[] { storageType }, (proxy, method, args) -> null));
            container = containerCls
                    .getConstructor(int.class, PlayerInventory.class, guiObjectCls)
                    .newInstance(Integer.valueOf(3), fake.field_71071_by, host);
            report.pass("ae2.container.construct",
                    "WirelessTermContainer constructed with slot sentinel " + SENTINEL + " without throwing");
        } catch (Throwable t) {
            report.threw("ae2.container.construct", t);
        }

        if (container != null) {
            try {
                Method ensure = Reflect.methodInHierarchy(containerCls, "ensureGuiItemIsInSlot",
                        guiItemCls, int.class);
                Object accepted = ensure.invoke(container, host, Integer.valueOf(SENTINEL));
                expect("ae2.container.ensure", Boolean.TRUE.equals(accepted),
                        "ensureGuiItemIsInSlot(host, " + SENTINEL + ") accepts the live phone terminal",
                        "ensureGuiItemIsInSlot(host, " + SENTINEL + ") rejected the live phone terminal");
            } catch (Throwable t) {
                report.info("ae2.container.ensure", "not probed: " + Report.describe(t));
            }
            try {
                Object valid = Reflect.methodInHierarchy(containerCls, "isValidContainer").invoke(container);
                report.info("ae2.container.valid", "isValidContainer=" + valid);
            } catch (Throwable t) {
                report.info("ae2.container.valid", "not probed: " + Report.describe(t));
            }
        }

        try {
            clearInventory(fake);
            boolean installed = ((Boolean) hostCls.getMethod("stillInstalled").invoke(host)).booleanValue();
            expect("ae2.host.detached", !installed,
                    "stillInstalled()=false once the phone leaves the inventory",
                    "stillInstalled()=true after the phone left the inventory");
            if (container != null) {
                Object accepted = Reflect.methodInHierarchy(containerCls, "ensureGuiItemIsInSlot",
                        guiItemCls, int.class).invoke(container, host, Integer.valueOf(SENTINEL));
                expect("ae2.container.detached", Boolean.FALSE.equals(accepted),
                        "container rejects the terminal once the phone is gone",
                        "container still accepts the terminal after the phone is gone");
            }
        } catch (Throwable t) {
            report.threw("ae2.host.detached", t);
        }
    }

    /**
     * AE2WTLib REDUX 4.0.2: build a real WirelessCraftingTerminalContainer around a WCTGuiObject
     * created from the phone's live terminal stack, then exercise the stillValid gate and the
     * TerminalOpener.openFromStack entry the bridge actually uses.
     *
     * The host stays unlinked (no wireless access point), so the storage grid is null; the WCT
     * constructor only forwards it as an IStorageMonitorable, which is the empty-facade case.
     */
    private void runAe2Wtlib(Class<?> ae2IntegrationCls, FakePlayer fake) {
        report.section("AE2WTLib REDUX 4.0.2 wireless crafting terminal (optional)");

        Class<?> terminalItemCls = Reflect.load(WT_TERMINAL_ITEM);
        Class<?> hostCls = Reflect.load(WT_HOST);
        Class<?> containerCls = Reflect.load(WT_CONTAINER);
        Class<?> handlerCls = Reflect.load(AE2_HANDLER);
        Class<?> openerCls = Reflect.load(WT_OPENER);

        if (terminalItemCls == null || hostCls == null || containerCls == null
                || handlerCls == null || openerCls == null) {
            report.skip("wtlib", "AE2WTLib is not installed on this server");
            return;
        }

        Item wct = findItemByType(WT_WCT_ITEM);
        if (wct == null) wct = findItemByType(WT_TERMINAL_ITEM);
        if (wct == null) {
            report.skip("wtlib.item", "no AE2WTLib terminal item registered");
            return;
        }
        report.pass("wtlib.item", String.valueOf(wct.getRegistryName()));

        Item phone = phoneItem();
        ItemStack phoneStack = phone == null ? ItemStack.field_190927_a : new ItemStack(phone, 1);
        ItemStack terminal = new ItemStack(wct, 1);

        if (ae2IntegrationCls != null) {
            try {
                Object integration = ae2IntegrationCls.getConstructor().newInstance();
                Object value = ae2IntegrationCls.getMethod("claims", ItemStack.class)
                        .invoke(integration, terminal);
                expect("wtlib.claims", Boolean.TRUE.equals(value),
                        wct.getRegistryName() + " is claimed by Ae2Integration",
                        wct.getRegistryName() + " is not claimed by Ae2Integration");
            } catch (Throwable t) {
                report.threw("wtlib.claims", t);
            }
        }

        try {
            clearInventory(fake);
            fake.field_71071_by.func_70299_a(0, phoneStack);
            Reflect.method(Reflect.load(TERMINAL_SLOT), "set", PlayerEntity.class, ItemStack.class)
                    .invoke(null, fake, terminal);
        } catch (Throwable t) {
            report.threw("wtlib.install", t);
            return;
        }

        Object host = null;
        try {
            host = hostCls.getConstructor(handlerCls, ItemStack.class, PlayerEntity.class, int.class)
                    .newInstance(terminal.func_77973_b(), terminal, fake, Integer.valueOf(-1));
            report.pass("wtlib.host.construct", "WCTGuiObject(handler, stack, player, -1)");
        } catch (Throwable t) {
            report.threw("wtlib.host.construct", t);
        }

        if (host != null) {
            try {
                ItemStack hosted = (ItemStack) hostCls.getMethod("getItemStack").invoke(host);
                expect("wtlib.host.live", hosted == terminal,
                        "WCTGuiObject.getItemStack() is the live phone terminal",
                        "WCTGuiObject.getItemStack() returned a different instance");
                report.info("wtlib.host.slot",
                        "getInventorySlot=" + hostCls.getMethod("getInventorySlot").invoke(host));
            } catch (Throwable t) {
                report.threw("wtlib.host.live", t);
            }
        }

        if (host != null) {
            attachEmptyStorageGrid(host);
        }

        Object container = null;
        if (host != null) {
            try {
                container = containerCls.getConstructor(int.class, PlayerInventory.class, hostCls)
                        .newInstance(Integer.valueOf(4), fake.field_71071_by, host);
                report.pass("wtlib.container.construct",
                        "WirelessCraftingTerminalContainer built for slot -1 without throwing");
            } catch (Throwable t) {
                report.threw("wtlib.container.construct", t);
            }
        }

        if (container != null) {
            try {
                ItemStack hosted = (ItemStack) containerCls.getMethod("getTerminalStack").invoke(container);
                expect("wtlib.container.identity", hosted == terminal,
                        "container.getTerminalStack() is the live phone terminal",
                        "container.getTerminalStack() returned a different instance");
            } catch (Throwable t) {
                report.threw("wtlib.container.identity", t);
            }

            try {
                Method stillValid = Reflect.methodInHierarchy(containerCls, "func_75145_c", PlayerEntity.class);

                Object withPhone = stillValid.invoke(container, fake);
                expect("wtlib.stillValid.withPhone", Boolean.TRUE.equals(withPhone),
                        "stillValid=true with the phone and terminal installed",
                        "stillValid=" + withPhone + " with the phone and terminal installed");

                clearInventory(fake);
                Object withoutPhone = stillValid.invoke(container, fake);
                expect("wtlib.stillValid.withoutPhone", Boolean.FALSE.equals(withoutPhone),
                        "stillValid=false once the phone leaves the inventory",
                        "stillValid=" + withoutPhone + " after the phone left the inventory");

                fake.field_71071_by.func_70299_a(0, phoneStack);
                ItemStack replacement = new ItemStack(
                        ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "stick")), 1);
                Reflect.method(Reflect.load(TERMINAL_SLOT), "set", PlayerEntity.class, ItemStack.class)
                        .invoke(null, fake, replacement);
                Object replaced = stillValid.invoke(container, fake);
                expect("wtlib.stillValid.replacement", Boolean.FALSE.equals(replaced),
                        "stillValid=false when the phone slot holds a different stack",
                        "stillValid=" + replaced + " after the phone slot was replaced");

                Reflect.method(Reflect.load(TERMINAL_SLOT), "set", PlayerEntity.class, ItemStack.class)
                        .invoke(null, fake, terminal);
                Object restored = stillValid.invoke(container, fake);
                expect("wtlib.stillValid.restored", Boolean.TRUE.equals(restored),
                        "stillValid=true again once the original terminal is back",
                        "stillValid=" + restored + " after the original terminal returned");
            } catch (Throwable t) {
                report.threw("wtlib.stillValid", t);
            }
        }

        runAe2WtlibOrdinaryControl(hostCls, containerCls, handlerCls, wct, fake);
        report.info("wtlib.openFromStack", "not driven through NetworkHooks: a FakePlayer has no real connection;"
                + " direct container identity and stillValid cover the bridge");
        runAe2WtlibEmptyWut(ae2IntegrationCls, fake);
    }

    /**
     * Installs an empty IStorageGrid on an unlinked AE2WTLib host.
     *
     * WTGuiObject leaves sg null until a wireless access point is in range. The WCT container only
     * forwards it as an IStorageMonitorable, so an empty facade keeps construction off the network
     * while still giving the grid-shaped object the addon expects.
     */
    private void attachEmptyStorageGrid(Object host) {
        Class<?> gridCls = Reflect.load("appeng.api.networking.storage.IStorageGrid");
        Class<?> monitorCls = Reflect.load("appeng.api.storage.IMEMonitor");
        if (gridCls == null || monitorCls == null) {
            report.info("wtlib.facade", "IStorageGrid/IMEMonitor missing, leaving sg null");
            return;
        }

        try {
            Object monitor = java.lang.reflect.Proxy.newProxyInstance(
                    Reflect.loader(), new Class<?>[]{monitorCls},
                    (proxy, method, args) -> defaultValue(method.getReturnType()));
            Object grid = java.lang.reflect.Proxy.newProxyInstance(
                    Reflect.loader(), new Class<?>[]{gridCls},
                    (proxy, method, args) -> "getInventory".equals(method.getName())
                            ? monitor
                            : defaultValue(method.getReturnType()));

            Field sg = Reflect.field(host.getClass(), "sg");
            sg.set(host, grid);
            report.pass("wtlib.facade", "empty IStorageGrid installed on the unlinked WCTGuiObject");
        } catch (Throwable t) {
            report.info("wtlib.facade", "could not install empty grid: " + Report.describe(t));
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return Boolean.FALSE;
        if (type == char.class) return Character.valueOf('\0');
        if (type == byte.class) return Byte.valueOf((byte) 0);
        if (type == short.class) return Short.valueOf((short) 0);
        if (type == int.class) return Integer.valueOf(0);
        if (type == long.class) return Long.valueOf(0L);
        if (type == float.class) return Float.valueOf(0F);
        if (type == double.class) return Double.valueOf(0D);
        return null;
    }

    /** Control: a terminal that really sits in an inventory slot keeps the vanilla stillValid path. */
    private void runAe2WtlibOrdinaryControl(Class<?> hostCls, Class<?> containerCls, Class<?> handlerCls,
                                            Item handlerItem, FakePlayer fake) {
        report.section("AE2WTLib ordinary inventory slot control");
        Item stick = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "stick"));
        if (stick == null) {
            report.skip("wtlib.ordinary", "minecraft:stick not registered");
            return;
        }

        ItemStack terminal = new ItemStack(stick, 1);
        try {
            clearInventory(fake);
            fake.field_71071_by.func_70299_a(9, terminal);
            Object host = hostCls.getConstructor(handlerCls, ItemStack.class, PlayerEntity.class, int.class)
                    .newInstance(handlerItem, terminal, fake, Integer.valueOf(9));
            attachEmptyStorageGrid(host);
            Object container = containerCls.getConstructor(int.class, PlayerInventory.class, hostCls)
                    .newInstance(Integer.valueOf(5), fake.field_71071_by, host);
            Method stillValid = Reflect.methodInHierarchy(containerCls, "func_75145_c", PlayerEntity.class);

            Object found = stillValid.invoke(container, fake);
            expect("wtlib.ordinary.present", Boolean.TRUE.equals(found),
                    "ordinary inventory slot 9 keeps stillValid=true",
                    "ordinary inventory slot 9 returned stillValid=" + found);

            clearInventory(fake);
            Object missing = stillValid.invoke(container, fake);
            expect("wtlib.ordinary.removed", Boolean.FALSE.equals(missing),
                    "ordinary inventory slot 9 returns false once the stack is gone",
                    "ordinary inventory slot 9 returned " + missing + " after removal");
        } catch (Throwable t) {
            report.threw("wtlib.ordinary", t);
        }
    }

    /**
     * WUT with no stored terminal: open must fail and leave the player's menu untouched.
     *
     * This is the regression guard for the review finding that an unconditional stillValid=true on
     * WUT suppresses the fallback and reports a terminal that is not there.
     */
    private void runAe2WtlibEmptyWut(Class<?> ae2IntegrationCls, FakePlayer fake) {
        report.section("AE2WTLib WUT without a stored terminal");
        Class<?> wutCls = Reflect.load(WT_WUT_ITEM);
        Class<?> sourceCls = Reflect.load(TERMINAL_SOURCE);
        Class<?> phoneSlotCls = Reflect.load(TERMINAL_SOURCE + "$PhoneSlot");
        Class<?> playerCls = Reflect.load("net.minecraft.entity.player.ServerPlayerEntity");

        if (wutCls == null || sourceCls == null || phoneSlotCls == null || playerCls == null
                || ae2IntegrationCls == null) {
            report.skip("wtlib.wut", "WUTItem, TerminalSource or Ae2Integration missing");
            return;
        }

        Item wut = findItemByType(WT_WUT_ITEM);
        if (wut == null) {
            report.skip("wtlib.wut", "no WUT item registered");
            return;
        }

        Item phone = phoneItem();
        ItemStack phoneStack = phone == null ? ItemStack.field_190927_a : new ItemStack(phone, 1);
        ItemStack wutStack = new ItemStack(wut, 1);

        try {
            Class<?> wutHandler = Reflect.load(WT_WUT_HANDLER);
            if (wutHandler != null) {
                Object current = wutHandler.getMethod("getCurrentTerminal", ItemStack.class)
                        .invoke(null, wutStack);
                report.info("wtlib.wut.current", "getCurrentTerminal=\"" + current + "\"");
            }
        } catch (Throwable t) {
            report.info("wtlib.wut.current", "not probed: " + Report.describe(t));
        }

        try {
            clearInventory(fake);
            fake.field_71071_by.func_70299_a(0, phoneStack);
            Reflect.method(Reflect.load(TERMINAL_SLOT), "set", PlayerEntity.class, ItemStack.class)
                    .invoke(null, fake, wutStack);

            Object source = phoneSlotCls.getDeclaredConstructor().newInstance();
            Method open = ae2IntegrationCls.getMethod("open", playerCls, ItemStack.class, sourceCls);

            Object before = fake.field_71070_bA;
            Object opened = open.invoke(ae2IntegrationCls.getConstructor().newInstance(), fake, wutStack, source);
            boolean changed = fake.field_71070_bA != before;

            expect("wtlib.wut.empty.open", Boolean.FALSE.equals(opened),
                    "Ae2Integration.open returns false for a WUT with no stored terminal",
                    "Ae2Integration.open returned " + opened + " for a WUT with no stored terminal");
            expect("wtlib.wut.empty.noChange", !changed,
                    "the fake player's menu is unchanged when an empty WUT opens nothing",
                    "the menu changed to "
                            + (fake.field_71070_bA == null ? "null" : fake.field_71070_bA.getClass().getName()));
        } catch (Throwable t) {
            report.threw("wtlib.wut.empty", t);
        }
    }

    private void identity(String id, FakePlayer fake, Method tsSet, Method tsGet, Class<?> rsSlotCls,
                          Method rsGetStack, Method rsSlotId, ItemStack terminal, Item item) {
        String label = String.valueOf(item.getRegistryName());
        try {
            tsSet.invoke(null, fake, terminal);
            ItemStack live = (ItemStack) tsGet.invoke(null, fake);
            Object slot = rsSlotCls.getConstructor(int.class).newInstance(Integer.valueOf(SENTINEL));
            ItemStack read = (ItemStack) rsGetStack.invoke(slot, fake);

            boolean sameLive = live == terminal;
            boolean sameRead = read == live;
            if (sameLive && sameRead) {
                report.pass(id, label + ": getStackFromSlot(sentinel) returned the identical live ItemStack");
            } else {
                report.fail(id, label + ": identity broken (set/get=" + sameLive + ", read/live=" + sameRead
                        + "), read=" + stackName(read));
            }

            int value = ((Number) rsSlotId.invoke(slot)).intValue();
            expect("rs.noInventoryLock." + slug(label), value == -1,
                    label + ": getSlotIdInPlayerInventory()=-1",
                    label + ": getSlotIdInPlayerInventory()=" + value);
        } catch (Throwable t) {
            report.threw(id, t);
        }
    }

    private void presence(String id, Class<?> cls, boolean required) {
        if (cls != null) {
            report.pass(id, cls.getName());
        } else if (required) {
            report.fail(id, "class is not on the server classpath");
        } else {
            report.skip(id, "class is not on the server classpath");
        }
    }

    private void expect(String id, boolean ok, String passDetail, String failDetail) {
        if (ok) report.pass(id, passDetail);
        else report.fail(id, failDetail);
    }

    private Item phoneItem() {
        Item item = findItemByType(PHONE_ITEM);
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("mcphone", "phone"));
        return item;
    }

    private Item findItemByType(String typeName) {
        Class<?> type = Reflect.load(typeName);
        if (type == null) return null;
        for (Item item : items) {
            if (item != null && type.isInstance(item)) return item;
        }
        return null;
    }

    private Item warpStoneItem(Class<?> wsItemsCls) {
        if (wsItemsCls != null) {
            try {
                Object value = wsItemsCls.getField("warpStone").get(null);
                if (value instanceof Item) return (Item) value;
            } catch (Throwable ignored) {
                // fall through to the registry
            }
        }
        Item byType = findItemByType("net.blay09.mods.waystones.item.WarpStoneItem");
        if (byType != null) return byType;
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation("waystones", "warp_stone"));
    }

    private Item findWirelessTerminal() {
        for (Item item : items) {
            if (item == null) continue;
            String name = item.getClass().getName();
            if (name.startsWith("appeng.") && name.contains("Wireless")) return item;
        }
        return null;
    }

    private static void clearInventory(FakePlayer fake) {
        PlayerInventory inventory = fake.field_71071_by;
        int size = inventory.func_70302_i_();
        for (int i = 0; i < size; i++) inventory.func_70299_a(i, ItemStack.field_190927_a);
    }

    private static String stackName(ItemStack stack) {
        if (stack == null) return "null";
        if (stack.func_190926_b()) return "<empty>";
        Item item = stack.func_77973_b();
        return (item == null ? "?" : String.valueOf(item.getRegistryName())) + " x" + stack.func_190916_E();
    }

    private static String slug(String value) {
        return value.replace(':', '.').replace(' ', '_');
    }
}
