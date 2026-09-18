package com.november.mcphone.core.script.server.economy;

/**
 * 这一次读不到，原因是 {@link #reasonKey()}（翻译键）。
 *
 * <p>给返回 {@code long} 的那几个方法用（{@code balance}）：它们没有位置放 {@code UNAVAILABLE}，
 * 返回 0 就是静默给错数 —— 调用方会以为这个人没钱。返回 {@code TxnResult} 的方法不抛这个，直接给 UNAVAILABLE。
 */
public final class CurrencyUnavailableException extends RuntimeException {

    private final String reasonKey;
    private final boolean refusedBeforeRunning;

    public CurrencyUnavailableException(String reasonKey) {
        this(reasonKey, false);
    }

    private CurrencyUnavailableException(String reasonKey, boolean refusedBeforeRunning) {
        super(reasonKey);
        this.reasonKey = reasonKey;
        this.refusedBeforeRunning = refusedBeforeRunning;
    }

    /** 网关拒掉的：provider 根本没被调用，什么都没动（{@link CurrencyGateway}）。 */
    static CurrencyUnavailableException refusedBeforeRunning(String reasonKey) {
        return new CurrencyUnavailableException(reasonKey, true);
    }

    public String reasonKey() {
        return reasonKey;
    }

    /** true = 网关拒的、provider 没跑；false = provider 自己抛的。 */
    boolean refusedBeforeRunning() {
        return refusedBeforeRunning;
    }
}
