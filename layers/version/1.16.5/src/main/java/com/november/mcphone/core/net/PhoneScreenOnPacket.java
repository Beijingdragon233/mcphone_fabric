package com.november.mcphone.core.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;

/** 客户端用两位掩码同步主手和副手的设备亮屏状态。 */
public final class PhoneScreenOnPacket {
    private final int litHands;

    public int litHands() { return litHands; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PhoneScreenOnPacket)) return false;
        PhoneScreenOnPacket that = (PhoneScreenOnPacket) other;
        return this.litHands == that.litHands;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(litHands);
        return hash;
    }

    @Override public String toString() { return "PhoneScreenOnPacket[litHands=" + litHands + "]"; }


    public static final int MAIN_HAND = 1;
    public static final int OFF_HAND = 2;
    public static final int ALL_HANDS = MAIN_HAND | OFF_HAND;

    public PhoneScreenOnPacket(int litHands) {
        litHands &= ALL_HANDS;
        this.litHands = litHands;
    }

    public boolean isLit(InteractionHand hand) {
        int bit = hand == InteractionHand.MAIN_HAND ? MAIN_HAND : OFF_HAND;
        return (litHands & bit) != 0;
    }

    public static void encode(PhoneScreenOnPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.litHands());
    }

    public static PhoneScreenOnPacket decode(FriendlyByteBuf buf) {
        return new PhoneScreenOnPacket(buf.readUnsignedByte());
    }
}
