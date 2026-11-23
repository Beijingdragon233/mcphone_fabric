package com.november.mcphone.api.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 一种货币的元数据（施工方案 §22.4）。
 *
 * <p><b>SDK 的任何逻辑都不依赖这里的任何一个字段</b>（§22.3 ①）：货币是注册表项，
 * 判定与运算一律按 {@link #id} 走。名字、符号、小数位、图标全是给界面看的。
 * 上一版 API 把单位名写进了类型（{@code IEmcWallet}），换个服务器就不通 —— 别再来一次。
 *
 * @param id          注册表标识，如 {@code myserver:coin}。脚本可读可比较
 * @param displayName 显示名，仅显示。<b>这是服主在 toml 里配的文本，不是 App 能写的</b> ——
 *                    别照着它给别的 SDK 也开自由文本的口子，那条路见 §23.3 对 titleKey 的要求
 * @param symbol      符号，如 {@code G}，仅显示
 * @param decimals    小数位 0..4，只影响显示与解析；金额本身永远是最小单位的整数（§22.3 ②）
 * @param icon        图标，可为 null
 */
public final class Currency {
    private final ResourceLocation id;
    private final Component displayName;
    private final String symbol;
    private final int decimals;
    private final ResourceLocation icon;

    public ResourceLocation id() { return id; }

    public Component displayName() { return displayName; }

    public String symbol() { return symbol; }

    public int decimals() { return decimals; }

    public ResourceLocation icon() { return icon; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Currency)) return false;
        Currency that = (Currency) other;
        return java.util.Objects.equals(this.id, that.id)
                && java.util.Objects.equals(this.displayName, that.displayName)
                && java.util.Objects.equals(this.symbol, that.symbol)
                && this.decimals == that.decimals
                && java.util.Objects.equals(this.icon, that.icon);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + java.util.Objects.hashCode(symbol);
        hash = 31 * hash + java.util.Objects.hashCode(decimals);
        hash = 31 * hash + java.util.Objects.hashCode(icon);
        return hash;
    }

    @Override public String toString() { return "Currency[id=" + id + ", displayName=" + displayName + ", symbol=" + symbol + ", decimals=" + decimals + ", icon=" + icon + "]"; }


    /** {@link #decimals} 的上限。再大就超出 {@code long} 能表达的金额范围（§22.4）。 */
    public static final int MAX_DECIMALS = 4;

    /** {@link #symbol} 的上限。它画在金额旁边，长了就把界面挤坏。 */
    public static final int MAX_SYMBOL = 8;

    public Currency(ResourceLocation id, Component displayName, String symbol, int decimals, ResourceLocation icon) {
        if (id == null) throw new IllegalArgumentException("货币 id 不能为 null");
        if (displayName == null) throw new IllegalArgumentException("displayName 不能为 null");
        if (symbol == null) symbol = "";
        if (symbol.length() > MAX_SYMBOL) {
            throw new IllegalArgumentException("symbol 最长 " + MAX_SYMBOL + "，收到 " + symbol.length());
        }
        if (decimals < 0 || decimals > MAX_DECIMALS) {
            throw new IllegalArgumentException("decimals 要在 0.." + MAX_DECIMALS + "，收到 " + decimals);
        }
        this.id = id;
        this.displayName = displayName;
        this.symbol = symbol;
        this.decimals = decimals;
        this.icon = icon;
    }
}
