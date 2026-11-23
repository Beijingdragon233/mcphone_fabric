package com.november.mcphone.feature.chat.profile.net;
import net.minecraft.network.FriendlyByteBuf;
/** 旧版整数通道只负责编解码。 */
public final class ProfileRequestPacket {
    private final ProfileWire.Query value;

    public ProfileRequestPacket(ProfileWire.Query value) {
        this.value = value;
    }

    public ProfileWire.Query value() { return value; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProfileRequestPacket)) return false;
        ProfileRequestPacket that = (ProfileRequestPacket) other;
        return java.util.Objects.equals(this.value, that.value);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(value);
        return hash;
    }

    @Override public String toString() { return "ProfileRequestPacket[value=" + value + "]"; }

    public static void encode(ProfileRequestPacket v, FriendlyByteBuf b) { ProfileWire.writeQuery(v.value(), b); }
    public static ProfileRequestPacket decode(FriendlyByteBuf b) { return new ProfileRequestPacket(ProfileWire.readQuery(b)); }
}
