package com.november.mcphone.feature.notes;

import com.google.common.collect.ImmutableList;
import com.november.mcphone.util.Java8Collections;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 记事本里的一整组笔记，跟着玩家走。整体不可变：每次改动产出新实例；
 * Codec 解出来的集合本身也是不可变的，按可变集合用会抛 UnsupportedOperationException。
 */
public final class NoteList {
    private final List<Note> notes;

    public List<Note> notes() { return notes; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NoteList)) return false;
        NoteList that = (NoteList) other;
        return java.util.Objects.equals(this.notes, that.notes);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(notes);
        return hash;
    }

    @Override public String toString() { return "NoteList[notes=" + notes + "]"; }


    public static final NoteList EMPTY = new NoteList(ImmutableList.of());

    public NoteList(List<Note> notes) {
        this.notes = ImmutableList.copyOf(notes);
    }

    public static final Codec<NoteList> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Note.CODEC.listOf().fieldOf("notes").forGetter(NoteList::notes)
            ).apply(instance, NoteList::new)
    );

    public static final int MAX_COUNT = 50;

    public boolean isEmpty() {
        return notes.isEmpty();
    }

    public Optional<Note> find(int id) {
        return notes.stream().filter(note -> note.id() == id).findFirst();
    }

    /** id 已存在就是改，否则新增；正文超长截断；新增时已满则原样返回 this，由界面提示 */
    public NoteList save(Note note) {
        String body = note.body().length() > Note.MAX_BODY_LENGTH
                ? note.body().substring(0, Note.MAX_BODY_LENGTH)
                : note.body();
        Note trimmed = new Note(note.id(), body, note.modified());

        List<Note> next = new ArrayList<>(notes);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id() == trimmed.id()) {
                next.set(i, trimmed);
                return new NoteList(next);
            }
        }

        if (next.size() >= MAX_COUNT) return this;

        next.add(trimmed);
        return new NoteList(next);
    }

    /** id 不存在时原样返回 this */
    public NoteList delete(int id) {
        if (!find(id).isPresent()) return this;
        return new NoteList(notes.stream().filter(note -> note.id() != id).collect(Java8Collections.toList()));
    }

    /** 取最大值加一而不是条数：删掉中间几条后按条数生成会撞号 */
    public int nextId() {
        return notes.stream().mapToInt(Note::id).max().orElse(0) + 1;
    }

    public List<Note> sortedByRecent() {
        return notes.stream()
                .sorted(Comparator.comparingLong(Note::modified).reversed())
                .collect(Java8Collections.toList());
    }
}
