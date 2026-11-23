package com.november.mcphone.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.function.Function;
import java.util.function.Supplier;

/** Forge 36 saves named objects in place. The adapter keeps the shared static NBT factories. */
public abstract class PhoneSavedData {
    private boolean dirty;

    protected abstract CompoundTag write(CompoundTag tag);

    public final CompoundTag save(CompoundTag tag) {
        return write(tag);
    }

    public void setDirty() {
        setDirty(true);
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return dirty;
    }

    protected static <T extends PhoneSavedData> T getOrCreate(
            MinecraftServer server, String name, Supplier<T> create, Function<CompoundTag, T> load) {
        Stored<T> stored = server.overworld().getDataStorage().computeIfAbsent(
                () -> new Stored<>(name, create, load), name);
        return stored.value();
    }

    private static final class Stored<T extends PhoneSavedData> extends SavedData {
        private final Supplier<T> create;
        private final Function<CompoundTag, T> decode;
        private T data;

        private Stored(String name, Supplier<T> create, Function<CompoundTag, T> decode) {
            super(name);
            this.create = create;
            this.decode = decode;
        }

        private T value() {
            if (data == null) data = create.get();
            return data;
        }

        @Override public void load(CompoundTag tag) {
            data = decode.apply(tag);
        }

        @Override public CompoundTag save(CompoundTag tag) {
            return value().save(tag);
        }

        @Override public boolean isDirty() {
            return data != null && data.isDirty();
        }

        @Override public void setDirty(boolean dirty) {
            PhoneSavedData state = value();
            state.dirty = dirty;
        }
    }
}
