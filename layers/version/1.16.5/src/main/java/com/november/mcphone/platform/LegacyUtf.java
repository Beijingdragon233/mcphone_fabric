package com.november.mcphone.platform;

import io.netty.handler.codec.EncoderException;
import net.minecraft.network.FriendlyByteBuf;

/** Maintains the shared packet character limit on 1.18.2, whose encoder takes a byte limit. */
public final class LegacyUtf {
    private final FriendlyByteBuf buffer;

    public LegacyUtf(FriendlyByteBuf buffer) {
        this.buffer = buffer;
    }

    public FriendlyByteBuf buffer() { return buffer; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LegacyUtf)) return false;
        LegacyUtf that = (LegacyUtf) other;
        return java.util.Objects.equals(this.buffer, that.buffer);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(buffer);
        return hash;
    }

    @Override public String toString() { return "LegacyUtf[buffer=" + buffer + "]"; }

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
