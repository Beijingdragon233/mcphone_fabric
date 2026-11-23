package com.november.mcphone.feature.chat.client;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

/** 不持有控件的列表几何；滚轮和字母跳转都受同一视口约束。 */
final class ChatContactScroll {
    static final int HEADER_HEIGHT = 8, CONTACT_HEIGHT = 19;
    static final class Row {
        private final String letter;
        private final ChatContactDirectory.Contact contact;
        private final int top;
        private final int height;

        Row(String letter, ChatContactDirectory.Contact contact, int top, int height) {
            this.letter = letter;
            this.contact = contact;
            this.top = top;
            this.height = height;
        }

        public String letter() { return letter; }

        public ChatContactDirectory.Contact contact() { return contact; }

        public int top() { return top; }

        public int height() { return height; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Row)) return false;
            Row that = (Row) other;
            return java.util.Objects.equals(this.letter, that.letter)
                    && java.util.Objects.equals(this.contact, that.contact)
                    && this.top == that.top
                    && this.height == that.height;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(letter);
            hash = 31 * hash + java.util.Objects.hashCode(contact);
            hash = 31 * hash + java.util.Objects.hashCode(top);
            hash = 31 * hash + java.util.Objects.hashCode(height);
            return hash;
        }

        @Override public String toString() { return "Row[letter=" + letter + ", contact=" + contact + ", top=" + top + ", height=" + height + "]"; }

        boolean heading() { return contact == null; }
    }
    private List<Row> rows = ImmutableList.of();
    private int contentHeight, offset;
    List<Row> rows() { return rows; }
    int offset() { return offset; }
    void reset() { offset = 0; }
    void clear() { rows = ImmutableList.of(); contentHeight = offset = 0; }
    void update(List<ChatContactDirectory.Section> sections, int viewportHeight) {
        ArrayList<ChatContactScroll.Row> next = new ArrayList<Row>();
        int y = 0;
        for (ChatContactDirectory.Section section : sections) {
            next.add(new Row(section.letter(), null, y, HEADER_HEIGHT));
            y += HEADER_HEIGHT;
            for (ChatContactDirectory.Contact contact : section.contacts()) {
                next.add(new Row(section.letter(), contact, y, CONTACT_HEIGHT));
                y += CONTACT_HEIGHT;
            }
        }
        rows = ImmutableList.copyOf(next);
        contentHeight = y;
        clamp(viewportHeight);
    }
    boolean scroll(double amount, int viewportHeight) {
        if (amount == 0) return false;
        int before = offset;
        offset += amount > 0 ? -CONTACT_HEIGHT : CONTACT_HEIGHT;
        clamp(viewportHeight);
        return offset != before;
    }
    boolean jump(String letter, int viewportHeight) {
        for (ChatContactScroll.Row row : rows) {
            if (row.heading() && row.letter().equals(letter)) {
                offset = row.top(); clamp(viewportHeight); return true;
            }
        }
        return false;
    }
    String firstLetter() {
        for (ChatContactScroll.Row row : rows) if (row.top() + row.height() > offset) return row.letter();
        return "";
    }
    private void clamp(int viewportHeight) {
        offset = Math.max(0, Math.min(offset, Math.max(0, contentHeight - Math.max(0, viewportHeight))));
    }
}
