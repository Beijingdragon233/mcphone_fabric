package mcphone1165fixture;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

/**
 * TEST-ONLY Forge 36 dedicated-server fixture for the MCphone 1.16.5 phone-slot bridge.
 *
 * This source tree lives under .build-tools/1165-fixture, outside production source, and is never
 * shipped. It exists to force-load PlayerWaystoneManager and RS PlayerSlot inside a real server and
 * assert the phone bridge with a Forge FakePlayer.
 */
@Mod(FixtureMod.MODID)
public final class FixtureMod {

    public static final String MODID = "mcphone1165fixture";

    public FixtureMod() {
        MinecraftForge.EVENT_BUS.register(new ServerStartedHandler());
    }
}
