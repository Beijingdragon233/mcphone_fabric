package com.november.mcphone.api.economy;

/**
 * {@link ICurrencyProvider#hold} 的结果（扩展施工方案 §22.4）。
 *
 * <p>§22.4 原型是 {@code EscrowId hold(...)} —— 返回值里<b>放不下失败原因</b>：
 * 余额不够与"货币用不了"都只能返回 null，而调用方对这两种的处理完全不同。
 *
 * @param result 成了就是 {@link TxnResult#OK}
 * @param id     成了才有，失败时为 null
 */
public final class HoldResult {
    private final TxnResult result;
    private final EscrowId id;

    public TxnResult result() { return result; }

    public EscrowId id() { return id; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HoldResult)) return false;
        HoldResult that = (HoldResult) other;
        return java.util.Objects.equals(this.result, that.result)
                && java.util.Objects.equals(this.id, that.id);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(result);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        return hash;
    }

    @Override public String toString() { return "HoldResult[result=" + result + ", id=" + id + "]"; }


    public HoldResult(TxnResult result, EscrowId id) {
        if (result == null) throw new IllegalArgumentException("HoldResult.result 不能为 null");
        if (result == TxnResult.OK && id == null) throw new IllegalArgumentException("OK 必须带 EscrowId");
        if (result != TxnResult.OK && id != null) throw new IllegalArgumentException("失败不许带 EscrowId");
        this.result = result;
        this.id = id;
    }

    public static HoldResult ok(EscrowId id) {
        return new HoldResult(TxnResult.OK, id);
    }

    public static HoldResult fail(TxnResult why) {
        return new HoldResult(why, null);
    }
}
