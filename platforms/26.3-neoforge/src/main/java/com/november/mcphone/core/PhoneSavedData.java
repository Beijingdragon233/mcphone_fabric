package com.november.mcphone.core;

import com.november.mcphone.MCphone;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 存档级数据的基类，把 {@link SavedData} 跨版本变了形状的两处关在这里。
 *
 * 26.3 的存储 API 改为 {@link SavedDataType}，存档序列化通过 {@link Codec} 提供。
 *
 * 子类只写 {@link #write} 与一个 {@code load(CompoundTag)}，两支同形，可以进共用层。
 * 别把 {@code HolderLookup.Provider} 漏进子类的签名 —— 那一支没有这个类型。
 */
public abstract class PhoneSavedData extends SavedData {

    /** 把自己写进 tag。返回值就是传进来的那个 tag。 */
    protected abstract CompoundTag write(CompoundTag tag);

    /** 取或建。Codec 只负责把新版存储 API 接回现有的裸 tag 读写逻辑。 */
    protected static <T extends PhoneSavedData> T getOrCreate(
            MinecraftServer server, String name, Supplier<T> create, Function<CompoundTag, T> load) {
        Codec<T> codec = CompoundTag.CODEC.xmap(
                tag -> load.apply(tag),
                value -> value.write(new CompoundTag()));
        SavedDataType<T> type = new SavedDataType<>(
                Identifier.fromNamespaceAndPath(MCphone.MODID, name), create, codec);
        return server.overworld().getDataStorage().computeIfAbsent(type);
    }
}
