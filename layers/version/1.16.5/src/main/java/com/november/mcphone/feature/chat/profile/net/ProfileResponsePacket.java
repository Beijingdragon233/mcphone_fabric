package com.november.mcphone.feature.chat.profile.net;
import net.minecraft.network.FriendlyByteBuf;
/** 旧版整数通道只负责编解码。 */
public final class ProfileResponsePacket {
    private final ProfileWire.Reply value;

    public ProfileResponsePacket(ProfileWire.Reply value) {
        this.value = value;
    }

    public ProfileWire.Reply value() { return value; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProfileResponsePacket)) return false;
        ProfileResponsePacket that = (ProfileResponsePacket) other;
        return java.util.Objects.equals(this.value, that.value);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(value);
        return hash;
    }

    @Override public String toString() { return "ProfileResponsePacket[value=" + value + "]"; }

    public static void encode(ProfileResponsePacket v, FriendlyByteBuf b) { ProfileWire.writeReply(v.value(), b); }
    public static ProfileResponsePacket decode(FriendlyByteBuf b) { return new ProfileResponsePacket(ProfileWire.readReply(b)); }
}
