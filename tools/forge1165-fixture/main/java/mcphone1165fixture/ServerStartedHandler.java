package mcphone1165fixture;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;

import java.io.File;

/** Runs the fixture once per server start and never lets a failure take the server down. */
public final class ServerStartedHandler {

    private boolean ran;

    @SubscribeEvent
    public void onServerStarted(FMLServerStartedEvent event) {
        if (ran) return;
        ran = true;

        Report report = new Report(new File("logs", "mcphone-1165-fixture.log"));
        try {
            FixtureRunner.run(event.getServer(), report);
        } catch (Throwable t) {
            report.fail("fixture.aborted", Report.describe(t));
            report.flush();
        }
    }
}
