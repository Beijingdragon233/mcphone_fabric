package com.november.mcphone.core.script.server;

import java.util.UUID;

/**
 * 求值时能看到的玩家信息（施工方案 §15.5：worker 只拿快照，不持有可变世界引用）。
 *
 * <p><b>这是一份拷贝，取的那一刻就开始过时。</b>任何要"现在是不是还成立"的判断
 * 都必须回主线程重查（§15.5 第二条）。
 *
 * <p>字段按 §16.5 的 {@code ctx.player} 定。留空接口等于什么都没定，
 * 而 S13 到货时再补字段就要改已经合并的签名。
 */
public final class PlayerSnapshot {
    private final UUID uuid;
    private final String name;
    private final String dimension;
    private final String gameMode;
    private final long onlineSinceEpochMs;

    public PlayerSnapshot(UUID uuid, String name, String dimension, String gameMode, long onlineSinceEpochMs) {
        this.uuid = uuid;
        this.name = name;
        this.dimension = dimension;
        this.gameMode = gameMode;
        this.onlineSinceEpochMs = onlineSinceEpochMs;
    }

    public UUID uuid() { return uuid; }

    public String name() { return name; }

    public String dimension() { return dimension; }

    public String gameMode() { return gameMode; }

    public long onlineSinceEpochMs() { return onlineSinceEpochMs; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PlayerSnapshot)) return false;
        PlayerSnapshot that = (PlayerSnapshot) other;
        return java.util.Objects.equals(this.uuid, that.uuid)
                && java.util.Objects.equals(this.name, that.name)
                && java.util.Objects.equals(this.dimension, that.dimension)
                && java.util.Objects.equals(this.gameMode, that.gameMode)
                && this.onlineSinceEpochMs == that.onlineSinceEpochMs;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(uuid);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(dimension);
        hash = 31 * hash + java.util.Objects.hashCode(gameMode);
        hash = 31 * hash + java.util.Objects.hashCode(onlineSinceEpochMs);
        return hash;
    }

    @Override public String toString() { return "PlayerSnapshot[uuid=" + uuid + ", name=" + name + ", dimension=" + dimension + ", gameMode=" + gameMode + ", onlineSinceEpochMs=" + onlineSinceEpochMs + "]"; }

}
