package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.PlayerAvatar;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 好友卡片的共用视图。普通联系人、建群和邀请使用同一布局，不持有选择或业务状态。 */
final class ChatContactRow {
    private ChatContactRow() {}
    static final class Layout {
        private final ChatLayout.Rect card;
        private final ChatLayout.Rect avatar;
        private final int textX;
        private final int textWidth;
        private final float nameCenterY;
        private final float statusCenterY;
        private final ChatLayout.Rect statusDot;
        private final ChatLayout.Rect choice;

        Layout(ChatLayout.Rect card, ChatLayout.Rect avatar, int textX, int textWidth, float nameCenterY, float statusCenterY, ChatLayout.Rect statusDot, ChatLayout.Rect choice) {
            this.card = card;
            this.avatar = avatar;
            this.textX = textX;
            this.textWidth = textWidth;
            this.nameCenterY = nameCenterY;
            this.statusCenterY = statusCenterY;
            this.statusDot = statusDot;
            this.choice = choice;
        }

        public ChatLayout.Rect card() { return card; }

        public ChatLayout.Rect avatar() { return avatar; }

        public int textX() { return textX; }

        public int textWidth() { return textWidth; }

        public float nameCenterY() { return nameCenterY; }

        public float statusCenterY() { return statusCenterY; }

        public ChatLayout.Rect statusDot() { return statusDot; }

        public ChatLayout.Rect choice() { return choice; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Layout)) return false;
            Layout that = (Layout) other;
            return java.util.Objects.equals(this.card, that.card)
                    && java.util.Objects.equals(this.avatar, that.avatar)
                    && this.textX == that.textX
                    && this.textWidth == that.textWidth
                    && Float.compare(this.nameCenterY, that.nameCenterY) == 0
                    && Float.compare(this.statusCenterY, that.statusCenterY) == 0
                    && java.util.Objects.equals(this.statusDot, that.statusDot)
                    && java.util.Objects.equals(this.choice, that.choice);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(card);
            hash = 31 * hash + java.util.Objects.hashCode(avatar);
            hash = 31 * hash + java.util.Objects.hashCode(textX);
            hash = 31 * hash + java.util.Objects.hashCode(textWidth);
            hash = 31 * hash + java.util.Objects.hashCode(nameCenterY);
            hash = 31 * hash + java.util.Objects.hashCode(statusCenterY);
            hash = 31 * hash + java.util.Objects.hashCode(statusDot);
            hash = 31 * hash + java.util.Objects.hashCode(choice);
            return hash;
        }

        @Override public String toString() { return "Layout[card=" + card + ", avatar=" + avatar + ", textX=" + textX + ", textWidth=" + textWidth + ", nameCenterY=" + nameCenterY + ", statusCenterY=" + statusCenterY + ", statusDot=" + statusDot + ", choice=" + choice + "]"; }
}

    static Layout layout(int x, int y, int width, int height, boolean selectable) {
        ChatLayout.Rect card = new ChatLayout.Rect(x, y, width, height - 1);
        float centerY = y + card.height() / 2f;
        ChatLayout.Rect avatar = new ChatLayout.Rect(x + 2, y + (card.height() - ChatLayout.AVATAR_SIZE) / 2,
                ChatLayout.AVATAR_SIZE, ChatLayout.AVATAR_SIZE);
        ChatLayout.Rect choice = selectable ? new ChatLayout.Rect(x + width - 11, y + (card.height() - 8) / 2, 8, 8) : null;
        int textX = x + ChatLayout.AVATAR_SIZE + 5;
        int textEnd = choice == null ? x + width - 3 : choice.x() - 4;
        return new Layout(card, avatar, textX, Math.max(1, textEnd - textX), centerY - 3.5f,
                centerY + 4, new ChatLayout.Rect(textX, (int) (centerY + 3), 2, 2), choice);
    }

    static void render(GuiGraphics g, Font font, ChatContactDirectory.Contact contact,
                       int x, int y, int width, int height, boolean hovered, boolean selectable, boolean selected) {
        ChatContactRow.Layout row = layout(x, y, width, height, selectable);
        ChatLayout.Rect card = row.card();
        ChatUi.glass(g, ChatGlass.Surface.CARD, card.x(), card.y(), card.width(), card.height(), hovered || selected);
        ChatLayout.Rect avatar = row.avatar(); PlayerAvatar.draw(g, contact.id(), avatar.x(), avatar.y(), avatar.width());
        ChatUi.label(g, font, ChatUi.truncate(font, contact.name(), row.textWidth(), ChatLayout.TEXT_SCALE),
                row.textX(), row.nameCenterY(), ChatLayout.TEXT_SCALE, FontPalette.title());
        ChatLayout.Rect dot = row.statusDot();
        g.fill(dot.x(), dot.y(), dot.x() + dot.width(), dot.y() + dot.height(),
                contact.online() ? PlayerAvatar.COLOR_ONLINE : PlayerAvatar.COLOR_OFFLINE);
        ChatUi.label(g, font, ChatUi.truncate(font, Component.translatable(contact.online()
                ? "mcphone.chat.contacts.online" : "mcphone.chat.contacts.offline").getString(),
                Math.max(1, row.textWidth() - 4), ChatLayout.META_SCALE),
                row.textX() + 4, row.statusCenterY(), ChatLayout.META_SCALE, FontPalette.subtle());
        if (selectable) {
            ChatLayout.Rect choice = row.choice();
            ChatUi.glass(g, ChatGlass.Surface.BUTTON, choice.x(), choice.y(), choice.width(), choice.height(), selected);
            if (selected) ChatUi.sizedIcon(g, ChatUi.Icon.CHECK, choice.x() + 1, choice.y() + 1, 6, ChatUi.accent());
        }
    }
}
