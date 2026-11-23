package com.november.mcphone.api.economy;

import java.util.UUID;

/**
 * 一笔托管的标识（施工方案 §22.4）。
 *
 * <p>包成 record 而不是直接用 {@link UUID}：托管 id、玩家 uuid、订单号在签名里都是 UUID，
 * 传错位置编译器看不出来。
 */
public final class EscrowId {
    private final UUID value;

    public UUID value() { return value; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EscrowId)) return false;
        EscrowId that = (EscrowId) other;
        return java.util.Objects.equals(this.value, that.value);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(value);
        return hash;
    }

    @Override public String toString() { return "EscrowId[value=" + value + "]"; }


    public EscrowId(UUID value) {
        if (value == null) throw new IllegalArgumentException("托管 id 不能为 null");
        this.value = value;
    }
}
