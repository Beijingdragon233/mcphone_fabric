package com.november.mcphone.platform;

import io.netty.handler.codec.EncoderException;
import net.minecraft.network.FriendlyByteBuf;

/** Maintains the shared packet character limit on 1.18.2, whose encoder takes a byte limit. */
public record LegacyUtf(FriendlyByteBuf buffer) {
    public FriendlyByteBuf writeUtf(String text, int maxCharacters) {
        if (text.length() > maxCharacters) {
            throw new EncoderException("String exceeds " + maxCharacters + " characters");
        }
        return buffer.writeUtf(text, Math.multiplyExact(maxCharacters, 3));
    }

    public FriendlyByteBuf writeUtf(String text) {
        return writeUtf(text, 32767);
    }
}
