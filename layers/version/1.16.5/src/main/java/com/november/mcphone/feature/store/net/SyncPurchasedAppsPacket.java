package com.november.mcphone.feature.store.net;

import com.november.mcphone.feature.store.PurchasedApps;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 网络包：服务端 → 客户端，下发这名玩家买过的全部 App。
 *
 * 整份下发而不是增量：这份集合最多几十条 id，比任何一次增量协议都便宜，
 * 也不存在"客户端漏了一条就永远对不上"的问题。购买成功后同样整份重发。
 *
 * 条数上限在 {@link PurchasedApps#decode} 层面封死。
 */
public final class SyncPurchasedAppsPacket {
    private final PurchasedApps purchased;

    public SyncPurchasedAppsPacket(PurchasedApps purchased) {
        this.purchased = purchased;
    }

    public PurchasedApps purchased() { return purchased; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SyncPurchasedAppsPacket)) return false;
        SyncPurchasedAppsPacket that = (SyncPurchasedAppsPacket) other;
        return java.util.Objects.equals(this.purchased, that.purchased);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(purchased);
        return hash;
    }

    @Override public String toString() { return "SyncPurchasedAppsPacket[purchased=" + purchased + "]"; }


    public static void encode(SyncPurchasedAppsPacket msg, FriendlyByteBuf buf) {
        PurchasedApps.encode(msg.purchased(), buf);
    }

    public static SyncPurchasedAppsPacket decode(FriendlyByteBuf buf) {
        return new SyncPurchasedAppsPacket(PurchasedApps.decode(buf));
    }

}
