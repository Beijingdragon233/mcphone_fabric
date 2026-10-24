package com.november.mcphone.feature.terminal.integration;

/**
 * Stable identities for the terminal integrations.
 *
 * <p>These constants live outside the optional implementation classes so the app
 * catalog can describe every possible companion without causing a missing
 * third-party API class to be loaded or linked.</p>
 */
public final class TerminalMods {

    public static final String AE2_MODID = "ae2";
    public static final String AE2_NAME = "Applied Energistics 2";

    public static final String REFINED_STORAGE_MODID = "refinedstorage";
    public static final String REFINED_STORAGE_NAME = "Refined Storage";

    public static final String TOMS_STORAGE_MODID = "toms_storage";
    public static final String TOMS_STORAGE_NAME = "Tom's Simple Storage Mod";

    private TerminalMods() {}
}
