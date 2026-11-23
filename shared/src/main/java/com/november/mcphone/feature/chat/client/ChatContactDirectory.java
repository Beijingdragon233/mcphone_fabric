package com.november.mcphone.feature.chat.client;

import com.november.mcphone.util.Java8;

import com.google.common.collect.ImmutableList;

import com.ibm.icu.text.AlphabeticIndex;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 纯数据目录：过滤、拼音排序和字母分组共用同一快照，不依赖网络或绘制。 */
final class ChatContactDirectory {
    static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
    static final class Contact {
        private final UUID id;
        private final String name;
        private final boolean online;

        Contact(UUID id, String name, boolean online) {
            this.id = id;
            this.name = name;
            this.online = online;
        }

        public UUID id() { return id; }

        public String name() { return name; }

        public boolean online() { return online; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Contact)) return false;
            Contact that = (Contact) other;
            return java.util.Objects.equals(this.id, that.id)
                    && java.util.Objects.equals(this.name, that.name)
                    && this.online == that.online;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(id);
            hash = 31 * hash + java.util.Objects.hashCode(name);
            hash = 31 * hash + java.util.Objects.hashCode(online);
            return hash;
        }

        @Override public String toString() { return "Contact[id=" + id + ", name=" + name + ", online=" + online + "]"; }
}
    static final class Section {
        private final String letter;
        private final List<Contact> contacts;

        public String letter() { return letter; }

        public List<Contact> contacts() { return contacts; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Section)) return false;
            Section that = (Section) other;
            return java.util.Objects.equals(this.letter, that.letter)
                    && java.util.Objects.equals(this.contacts, that.contacts);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(letter);
            hash = 31 * hash + java.util.Objects.hashCode(contacts);
            return hash;
        }

        @Override public String toString() { return "Section[letter=" + letter + ", contacts=" + contacts + "]"; }

        Section(String letter, List<Contact> contacts) { contacts = ImmutableList.copyOf(contacts);
            this.letter = letter;
            this.contacts = contacts;
        }
    }
    // 使用 Minecraft 自带 ICU。固定拼音规则，不能随操作系统语言改变排列或用 GBK 字节猜首字母。
    private final AlphabeticIndex.ImmutableIndex<Object> alphabet;
    private final Collator collator;

    ChatContactDirectory() {
        com.ibm.icu.text.AlphabeticIndex<Object> builder = new AlphabeticIndex<Object>(new ULocale("zh@collation=pinyin"))
                .addLabels(Locale.ENGLISH);
        alphabet = builder.buildImmutableIndex();
        collator = builder.getCollator();
    }

    String letter(String name) {
        String label = alphabet.getBucket(alphabet.getBucketIndex(displayKey(name))).getLabel();
        return label.length() == 1 && label.charAt(0) >= 'A' && label.charAt(0) <= 'Z' ? label : "#";
    }

    List<Section> sections(List<Contact> source, String query) {
        String needle = searchKey(query);
        ArrayList<ChatContactDirectory.Contact> sorted = new ArrayList<Contact>();
        for (Contact contact : source) {
            if (searchKey(contact.name()).contains(needle)) sorted.add(contact);
        }
        sorted.sort(Comparator.comparingInt((Contact c) -> LETTERS.indexOf(letter(c.name())))
                .thenComparing(Contact::name, (a, b) -> collator.compare(displayKey(a), displayKey(b)))
                .thenComparing(Contact::id));
        ArrayList<ChatContactDirectory.Section> sections = new ArrayList<Section>();
        for (int start = 0; start < sorted.size();) {
            String label = letter(sorted.get(start).name());
            int end = start + 1;
            while (end < sorted.size() && letter(sorted.get(end).name()).equals(label)) end++;
            sections.add(new Section(label, sorted.subList(start, end)));
            start = end;
        }
        return ImmutableList.copyOf(sections);
    }

    private static String displayKey(String value) {
        return Java8.strip(Normalizer.normalize(value, Normalizer.Form.NFKC));
    }
    static boolean matches(String name, String query) { return searchKey(name).contains(searchKey(query)); }
    private static String searchKey(String value) { return displayKey(value).toLowerCase(Locale.ROOT); }
}
