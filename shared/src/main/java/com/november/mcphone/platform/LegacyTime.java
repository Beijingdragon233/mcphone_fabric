package com.november.mcphone.platform;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class LegacyTime {
    private static final DateTimeFormatter FILENAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

    private LegacyTime() {}

    public static String filenameTimestamp() {
        return LocalDateTime.now().format(FILENAME);
    }
}
