package com.november.mcphone.feature.chat.client;

import com.november.mcphone.util.Java8Collections;

import java.util.List;

/** 个人卡片与四个入口的不可变排版；绘制和点击复用同一份边界。 */
final class ChatProfileLayout {
    static final class Home {
        private final ChatLayout.Rect card;
        private final ChatLayout.Rect avatar;
        private final List<ChatLayout.Rect> rows;
        private final float textX;
        private final float nameCenterY;
        private final float introCenterY;

        Home(ChatLayout.Rect card, ChatLayout.Rect avatar, List<ChatLayout.Rect> rows, float textX, float nameCenterY, float introCenterY) {
            this.card = card;
            this.avatar = avatar;
            this.rows = rows;
            this.textX = textX;
            this.nameCenterY = nameCenterY;
            this.introCenterY = introCenterY;
        }

        public ChatLayout.Rect card() { return card; }

        public ChatLayout.Rect avatar() { return avatar; }

        public List<ChatLayout.Rect> rows() { return rows; }

        public float textX() { return textX; }

        public float nameCenterY() { return nameCenterY; }

        public float introCenterY() { return introCenterY; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Home)) return false;
            Home that = (Home) other;
            return java.util.Objects.equals(this.card, that.card)
                    && java.util.Objects.equals(this.avatar, that.avatar)
                    && java.util.Objects.equals(this.rows, that.rows)
                    && Float.compare(this.textX, that.textX) == 0
                    && Float.compare(this.nameCenterY, that.nameCenterY) == 0
                    && Float.compare(this.introCenterY, that.introCenterY) == 0;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(card);
            hash = 31 * hash + java.util.Objects.hashCode(avatar);
            hash = 31 * hash + java.util.Objects.hashCode(rows);
            hash = 31 * hash + java.util.Objects.hashCode(textX);
            hash = 31 * hash + java.util.Objects.hashCode(nameCenterY);
            hash = 31 * hash + java.util.Objects.hashCode(introCenterY);
            return hash;
        }

        @Override public String toString() { return "Home[card=" + card + ", avatar=" + avatar + ", rows=" + rows + ", textX=" + textX + ", nameCenterY=" + nameCenterY + ", introCenterY=" + introCenterY + "]"; }
}

    private ChatProfileLayout() {}

    static Home home(ChatLayout.Rect area) {
        ChatLayout.Rect card = new ChatLayout.Rect(area.x(), area.y(), area.width(), 38);
        ChatLayout.Rect avatar = new ChatLayout.Rect(area.x() + 5, area.y() + 7, 24, 24);
        List<ChatLayout.Rect> rows = java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> new ChatLayout.Rect(area.x(), area.y() + 44 + i * 20, area.width(), 18))
                .collect(Java8Collections.toList());
        float centerY = card.y() + card.height() / 2f;
        return new Home(card, avatar, rows, area.x() + 35, centerY - 5.5f, centerY + 5.5f);
    }
}
