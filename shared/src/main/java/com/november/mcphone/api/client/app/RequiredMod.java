package com.november.mcphone.api.client.app;

/**
 * 一个 App 依赖的外部模组——「联动 App」那一页显示的就是它。刻意没有下载链接。
 *
 * @param modId       模组 id，与 {@code ModPresence.isLoaded} 用的是同一个值
 * @param displayName 给玩家看的名字，例如 "Waystones（传送石碑）"。自己写死，别在运行时查——要显示它的时候那个模组多半没装
 */
public final class RequiredMod {
    private final String modId;
    private final String displayName;

    public RequiredMod(String modId, String displayName) {
        this.modId = modId;
        this.displayName = displayName;
    }

    public String modId() { return modId; }

    public String displayName() { return displayName; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequiredMod)) return false;
        RequiredMod that = (RequiredMod) other;
        return java.util.Objects.equals(this.modId, that.modId)
                && java.util.Objects.equals(this.displayName, that.displayName);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(modId);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        return hash;
    }

    @Override public String toString() { return "RequiredMod[modId=" + modId + ", displayName=" + displayName + "]"; }
}
