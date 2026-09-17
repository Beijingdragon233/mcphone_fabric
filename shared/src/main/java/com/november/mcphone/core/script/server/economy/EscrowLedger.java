package com.november.mcphone.core.script.server.economy;

import com.november.mcphone.api.economy.EscrowId;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * 托管账（施工方案 §22.4）。<b>受益人在创建时定死，之后不可更改。</b>
 *
 * <h2>必须持久化</h2>
 *
 * 落在 {@code world/mcphone/economy/escrow.dat}：<b>服务器重启不能把托管中的钱吃掉</b>。
 * 启动时扫一遍，超过 {@link #DEFAULT_TIMEOUT_MS} 的自动退款并记流水。
 *
 * <p>这个类只管账本逻辑；序列化由调用方接到 SavedData 上 —— 那是平台侧的事。
 *
 * <h2>重复结算要认得出来</h2>
 *
 * 已经放过款或退过款的再来一次，返回 {@code ALREADY_SETTLED} 而不是 {@code FAILED}：
 * 调用方据此知道"这笔已经成了"，而不是"出错了要重试"。
 */
public final class EscrowLedger {

    /** 托管超时，默认 7 天（§22.4）。到点自动退款。 */
    public static final long DEFAULT_TIMEOUT_MS = 7L * 24 * 3600 * 1000;

    /** 一笔托管。 */
    public record Entry(UUID owner, UUID beneficiary, String currencyId, long amount,
                        long createdAt, boolean settled) {
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    private final LongSupplier clock;
    private final long timeoutMs;

    public EscrowLedger(LongSupplier clock) {
        this(clock, DEFAULT_TIMEOUT_MS);
    }

    public EscrowLedger(LongSupplier clock, long timeoutMs) {
        this.clock = clock;
        this.timeoutMs = timeoutMs;
    }

    /** 建一笔。钱已经从 owner 身上扣掉了 —— 这里只记账。 */
    public EscrowId create(UUID owner, UUID beneficiary, String currencyId, long amount) {
        UUID id = UUID.randomUUID();
        entries.put(id, new Entry(owner, beneficiary, currencyId, amount, clock.getAsLong(), false));
        return new EscrowId(id);
    }

    /** 查。不认识返回 null。 */
    public Entry get(EscrowId id) {
        return id == null ? null : entries.get(id.value());
    }

    /** 标记结算。返回 false 表示这笔已经结过了。 */
    public boolean settle(EscrowId id) {
        Entry e = entries.get(id.value());
        if (e == null || e.settled()) return false;
        entries.put(id.value(), new Entry(e.owner(), e.beneficiary(), e.currencyId(),
                e.amount(), e.createdAt(), true));
        return true;
    }

    /** 还没结算的托管里，这种货币一共押着多少钱。<b>对账要把它算进总量</b>（§22.10）。 */
    public long held(String currencyId) {
        long n = 0;
        for (Entry e : entries.values()) {
            if (!e.settled() && e.currencyId().equals(currencyId)) n += e.amount();
        }
        return n;
    }

    /** 超时的那些（§22.4 默认 7 天）。启动时扫一遍，逐个退款。 */
    public java.util.List<Map.Entry<EscrowId, Entry>> expired() {
        long now = clock.getAsLong();
        java.util.List<Map.Entry<EscrowId, Entry>> out = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            Entry v = e.getValue();
            if (!v.settled() && now - v.createdAt() >= timeoutMs) {
                out.add(Map.entry(new EscrowId(e.getKey()), v));
            }
        }
        return out;
    }

    /** 落盘用：全部条目。 */
    public Map<UUID, Entry> snapshot() {
        return Map.copyOf(entries);
    }

    /** 从盘上读回来。 */
    public void restore(Map<UUID, Entry> saved) {
        entries.clear();
        entries.putAll(saved);
    }

    public int size() {
        return entries.size();
    }
}
