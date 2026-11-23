package com.november.mcphone.feature.music;

import com.november.mcphone.platform.StackCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * 手机唱片仓里的东西，存为玩家附件。
 * "在不在放"不存 boolean 而存开始时刻：服务端没有 tick 盯着唱片放完，现算 startedTick + 长度 即可；
 * 用游戏刻不用系统时间：与 JukeboxSong.lengthInTicks 同单位，且服务端权威。
 *
 * @param disc        空栈表示没放
 * @param startedTick 开始外放的游戏刻，-1 表示没在放
 */
public final class DiscState {
    private final ItemStack disc;
    private final long startedTick;

    public DiscState(ItemStack disc, long startedTick) {
        this.disc = disc;
        this.startedTick = startedTick;
    }

    public ItemStack disc() { return disc; }

    public long startedTick() { return startedTick; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiscState)) return false;
        DiscState that = (DiscState) other;
        return java.util.Objects.equals(this.disc, that.disc)
                && this.startedTick == that.startedTick;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(disc);
        hash = 31 * hash + java.util.Objects.hashCode(startedTick);
        return hash;
    }

    @Override public String toString() { return "DiscState[disc=" + disc + ", startedTick=" + startedTick + "]"; }


    /** "没在放"哨兵；startedTick 与下发给客户端的终点刻共用这一个值 */
    public static final long NOT_PLAYING = -1L;

    public static final DiscState EMPTY = new DiscState(ItemStack.EMPTY, NOT_PLAYING);

    public static final Codec<DiscState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    // 可空的物品堆字段：两支表达方式不同（连落盘形状都不同），
                    // 差别关在 StackCodecs 里
                    StackCodecs.optionalStackField("disc").forGetter(DiscState::disc),
                    Codec.LONG.fieldOf("started_tick").forGetter(DiscState::startedTick)
            ).apply(instance, DiscState::new)
    );

    public boolean hasDisc() {
        return !disc.isEmpty();
    }

    /** 换一张唱片，同时停掉正在放的 —— 换碟当然要从头来 */
    public DiscState withDisc(ItemStack stack) {
        return new DiscState(stack, -1L);
    }

    public DiscState playingSince(long tick) {
        return new DiscState(disc, tick);
    }

    public DiscState stopped() {
        return startedTick < 0 ? this : new DiscState(disc, -1L);
    }

    /** 循环播放时覆盖当前时刻的轮次起点；纯算术见 {@link DiscLoop#periodStart}。 */
    public static long loopPeriodStart(long startedTick, long length, long now) {
        return DiscLoop.periodStart(startedTick, length, now);
    }
}
