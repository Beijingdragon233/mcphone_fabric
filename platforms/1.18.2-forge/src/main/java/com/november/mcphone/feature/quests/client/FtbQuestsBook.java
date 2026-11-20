package com.november.mcphone.feature.quests.client;

import com.november.mcphone.MCphone;
import com.november.mcphone.platform.ModPresence;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** FTB Quests 1.18.2 opens the quest GUI through the current client quest file. */
public final class FtbQuestsBook {
    public static final String FTBQUESTS_MODID = "ftbquests";
    private static boolean resolved;
    private static Field instance;
    private static Method openGui;

    private FtbQuestsBook() {}

    public static boolean isLoaded() {
        return ModPresence.isLoaded(FTBQUESTS_MODID);
    }

    public static boolean open() {
        if (!isLoaded()) return false;
        if (!resolved) {
            resolved = true;
            try {
                var questFile = Class.forName("dev.ftb.mods.ftbquests.client.ClientQuestFile");
                instance = questFile.getField("INSTANCE");
                openGui = questFile.getMethod("openQuestGui");
            } catch (ReflectiveOperationException | LinkageError e) {
                MCphone.LOGGER.warn("[MCphone] FTB Quests 客户端入口不可用", e);
            }
        }
        if (openGui == null) return false;
        try {
            Object file = instance.get(null);
            if (file == null) return false;
            openGui.invoke(file);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            MCphone.LOGGER.warn("[MCphone] 无法打开 FTB 任务书", e);
            return false;
        }
    }
}
