package com.november.mcphone.platform.client;

import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.sounds.AudioStream;
import com.mojang.blaze3d.audio.OggAudioStream;

import java.io.IOException;
import java.io.InputStream;

public final class VanillaAudio {
    private VanillaAudio() {}

    public static AudioStream openOgg(InputStream in) throws IOException {
        return new OggAudioStream(in);
    }

    public static void initialize(Library library) {
        library.init(null, false);
    }
}
