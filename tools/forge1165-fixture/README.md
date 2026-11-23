# Forge 36 dedicated-server bridge fixture

This test mod is separate from the production MCPhone JAR. It requires the fixed addon versions
in `docs/PORT-1.16.5-FORGE.md` and a standard Forge 36.2.42 server. It must not ship to players.

Build on Windows with explicit Java 8 and server paths:

```powershell
./build.ps1 -JavaHome 'D:/jdk8' -AddonsRoot 'D:/forge1165-test'
```

Copy `dist/mcphone-1165-fixture-1.0.0.jar` into that server's mods directory alongside the production
MCPhone JAR and the fixed addons. Start Forge normally. Read `logs/mcphone-1165-fixture.log` and
require `FIXTURE_RESULT PASS`, with no failed or skipped checks.

The fixture uses a FakePlayer, restores its inventory/menu, verifies live terminal identity,
Refined Storage sentinel serialization, ordinary inventory controls, and Waystones menu/phone gates.
AE2's real WirelessTermContainer is constructed using an empty storage-grid facade injected into the
test host solely to reach slot locking and host liveness. **That facade does not validate network
binding, power, permissions, storage transactions or a usable AE2 GUI.** Those remain gameplay checks.

The recorded run passed 47 checks on Java 1.8.0_514 and Forge 36.2.42. The text report is in
`docs/validation/1.16.5-forge/bridge-fixture.txt`. A previous unbound-host attempt failed because AE2
requires a storage grid; the fixture now explicitly supplies the limited facade described above.
