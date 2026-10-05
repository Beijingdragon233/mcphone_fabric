package com.november.mcphone.platform.client;

import com.mojang.blaze3d.audio.DeviceList;
import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;

import java.io.IOException;
import java.io.InputStream;

/** Opens an Ogg stream using the 26.3 vanilla decoder. */
public final class VanillaAudio {
    private VanillaAudio() {}

    public static AudioStream openOgg(InputStream in) throws IOException {
        return new JOrbisAudioStream(in);
    }

    public static void initialize(Library library) {
        library.init(null, DeviceList.query(), false);
    }
}
