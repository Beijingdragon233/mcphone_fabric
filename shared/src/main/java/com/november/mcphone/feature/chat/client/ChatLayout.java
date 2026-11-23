package com.november.mcphone.feature.chat.client;

import com.november.mcphone.util.Java8;

/** 聊天页的逻辑像素布局。字号换算与输入栏分区共用一份，绘制和点击不能各算各的。 */
public final class ChatLayout {
    private ChatLayout() {}

    public static final float TEXT_SCALE = 0.75f;
    public static final float META_SCALE = 0.60f;
    public static final float GROUP_NAME_SCALE = 0.50f;
    public static final float TAB_LABEL_SCALE = 0.45f;
    public static final float SEND_SCALE = 0.60f;
    public static final int ICON_SIZE = 9;
    public static final int AVATAR_SIZE = 12;
    public static final int MESSAGE_AVATAR_GAP = 3;
    private static final int GROUP_NAME_TOP = 1;
    private static final int GROUP_NAME_GAP = 1;
    public static final int HEADER_HEIGHT = 12;
    public static final int SEARCH_HEIGHT = 12;
    public static final int SEARCH_ICON_SIZE = 6;
    public static final int SEARCH_PAD_X = 3;
    public static final int SEARCH_TEXT_X = SEARCH_PAD_X + SEARCH_ICON_SIZE + 3;
    public static final int TOP_GAP = 2;
    public static final int SECTION_GAP = 2;
    public static final int INPUT_HEIGHT = 12;
    public static final int SEND_HEIGHT = 10;
    public static final int TAB_HEIGHT = 18;
    public static final int TAB_ICON_TOP = 2;
    public static final int TAB_LABEL_TOP = 12;
    public static final int CARD_PAD = 2;
    public static final int CARD_GAP = 2;
    public static final int AVATAR_FRAME = 1;
    public static final int AVATAR_STATUS_SIZE = 2;
    public static final int AVATAR_TEXT_GAP = 3;
    public static final int CARD_ACTION_GAP = 3;
    public static final int UNREAD_HEIGHT = 8;
    public static final int TELEPORT_ICON_SIZE = 7;
    public static final int TELEPORT_HIT_PAD = 3;
    private static final int GAP = 2;

    /** 向下取整，折行后的文字才不会越过气泡或输入框的右边界。 */
    public static int unscaledWidth(int width, float scale) {
        return Math.max(1, (int) Math.floor(width / scale));
    }

    public static int scaledWidth(int width, float scale) {
        // 0.6f 的表示误差会把整像素的 42 算成 42.000002；不能因此多占一列。
        return (int) Math.ceil(width * (double) scale - 0.0001);
    }

    /** 沿用已校准的默认字形高度及半像素顶沿偏移；保留小数，不用七像素估算中文。 */
    static float labelTop(int lineHeight, float scale, float centerY) {
        return centerY - Math.max(1, lineHeight - 1) * scale / 2f - scale / 2f;
    }

    /** 默认字体末尾字间距不参与标签的视觉中心。 */
    static float labelLeft(int advance, float scale, float centerX) {
        return centerX - Math.max(0, advance - 1) * scale / 2f;
    }

    public static boolean hasText(String text) {
        return text != null && !Java8.isBlank(text);
    }

    /** 空白时加号只占图标宽，有文字时才留发送按钮宽；表情始终与右侧动作相隔两个像素。 */
    public static Composer composer(int width, int sendLabelWidth, boolean hasText) {
        int actionWidth = hasText ? Math.max(14, sendLabelWidth + 4) : ICON_SIZE;
        int actionX = width - actionWidth;
        int stickerX = actionX - GAP - ICON_SIZE;
        int inputX = ICON_SIZE + GAP;
        int inputWidth = stickerX - GAP - inputX;
        return new Composer(inputX, inputWidth, stickerX, actionX, actionWidth);
    }

    public static final class Composer {
        private final int inputX;
        private final int inputWidth;
        private final int stickerX;
        private final int actionX;
        private final int actionWidth;

        public Composer(int inputX, int inputWidth, int stickerX, int actionX, int actionWidth) {
            this.inputX = inputX;
            this.inputWidth = inputWidth;
            this.stickerX = stickerX;
            this.actionX = actionX;
            this.actionWidth = actionWidth;
        }

        public int inputX() { return inputX; }

        public int inputWidth() { return inputWidth; }

        public int stickerX() { return stickerX; }

        public int actionX() { return actionX; }

        public int actionWidth() { return actionWidth; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Composer)) return false;
            Composer that = (Composer) other;
            return this.inputX == that.inputX
                    && this.inputWidth == that.inputWidth
                    && this.stickerX == that.stickerX
                    && this.actionX == that.actionX
                    && this.actionWidth == that.actionWidth;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(inputX);
            hash = 31 * hash + java.util.Objects.hashCode(inputWidth);
            hash = 31 * hash + java.util.Objects.hashCode(stickerX);
            hash = 31 * hash + java.util.Objects.hashCode(actionX);
            hash = 31 * hash + java.util.Objects.hashCode(actionWidth);
            return hash;
        }

        @Override public String toString() { return "Composer[inputX=" + inputX + ", inputWidth=" + inputWidth + ", stickerX=" + stickerX + ", actionX=" + actionX + ", actionWidth=" + actionWidth + "]"; }
}

    /** 名称一行、摘要一行；底部同时容纳未读数与传送图标。 */
    public static int cardHeight(int nameLineHeight, int previewLineHeight) {
        return Math.max(AVATAR_SIZE + AVATAR_FRAME * 2,
                nameLineHeight + 1 + Math.max(previewLineHeight, UNREAD_HEIGHT)) + CARD_PAD * 2;
    }

    public static final class Rect {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        public Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int x() { return x; }

        public int y() { return y; }

        public int width() { return width; }

        public int height() { return height; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Rect)) return false;
            Rect that = (Rect) other;
            return this.x == that.x
                    && this.y == that.y
                    && this.width == that.width
                    && this.height == that.height;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(x);
            hash = 31 * hash + java.util.Objects.hashCode(y);
            hash = 31 * hash + java.util.Objects.hashCode(width);
            hash = 31 * hash + java.util.Objects.hashCode(height);
            return hash;
        }

        @Override public String toString() { return "Rect[x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]"; }

        public boolean contains(double mx, double my) { return hit(mx, my, x, y, width, height); }
    }

    public static final class Card {
        private final int height;
        private final Rect avatarFrame;
        private final int textX;
        private final int nameY;
        private final int nameWidth;
        private final int timeX;
        private final float timeY;
        private final float previewY;
        private final int previewWidth;
        private final float teleportX;
        private final float teleportY;
        private final Rect teleportHit;
        private final Rect unread;

        public Card(int height, Rect avatarFrame, int textX, int nameY, int nameWidth, int timeX, float timeY, float previewY, int previewWidth, float teleportX, float teleportY, Rect teleportHit, Rect unread) {
            this.height = height;
            this.avatarFrame = avatarFrame;
            this.textX = textX;
            this.nameY = nameY;
            this.nameWidth = nameWidth;
            this.timeX = timeX;
            this.timeY = timeY;
            this.previewY = previewY;
            this.previewWidth = previewWidth;
            this.teleportX = teleportX;
            this.teleportY = teleportY;
            this.teleportHit = teleportHit;
            this.unread = unread;
        }

        public int height() { return height; }

        public Rect avatarFrame() { return avatarFrame; }

        public int textX() { return textX; }

        public int nameY() { return nameY; }

        public int nameWidth() { return nameWidth; }

        public int timeX() { return timeX; }

        public float timeY() { return timeY; }

        public float previewY() { return previewY; }

        public int previewWidth() { return previewWidth; }

        public float teleportX() { return teleportX; }

        public float teleportY() { return teleportY; }

        public Rect teleportHit() { return teleportHit; }

        public Rect unread() { return unread; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Card)) return false;
            Card that = (Card) other;
            return this.height == that.height
                    && java.util.Objects.equals(this.avatarFrame, that.avatarFrame)
                    && this.textX == that.textX
                    && this.nameY == that.nameY
                    && this.nameWidth == that.nameWidth
                    && this.timeX == that.timeX
                    && Float.compare(this.timeY, that.timeY) == 0
                    && Float.compare(this.previewY, that.previewY) == 0
                    && this.previewWidth == that.previewWidth
                    && Float.compare(this.teleportX, that.teleportX) == 0
                    && Float.compare(this.teleportY, that.teleportY) == 0
                    && java.util.Objects.equals(this.teleportHit, that.teleportHit)
                    && java.util.Objects.equals(this.unread, that.unread);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(height);
            hash = 31 * hash + java.util.Objects.hashCode(avatarFrame);
            hash = 31 * hash + java.util.Objects.hashCode(textX);
            hash = 31 * hash + java.util.Objects.hashCode(nameY);
            hash = 31 * hash + java.util.Objects.hashCode(nameWidth);
            hash = 31 * hash + java.util.Objects.hashCode(timeX);
            hash = 31 * hash + java.util.Objects.hashCode(timeY);
            hash = 31 * hash + java.util.Objects.hashCode(previewY);
            hash = 31 * hash + java.util.Objects.hashCode(previewWidth);
            hash = 31 * hash + java.util.Objects.hashCode(teleportX);
            hash = 31 * hash + java.util.Objects.hashCode(teleportY);
            hash = 31 * hash + java.util.Objects.hashCode(teleportHit);
            hash = 31 * hash + java.util.Objects.hashCode(unread);
            return hash;
        }

        @Override public String toString() { return "Card[height=" + height + ", avatarFrame=" + avatarFrame + ", textX=" + textX + ", nameY=" + nameY + ", nameWidth=" + nameWidth + ", timeX=" + timeX + ", timeY=" + timeY + ", previewY=" + previewY + ", previewWidth=" + previewWidth + ", teleportX=" + teleportX + ", teleportY=" + teleportY + ", teleportHit=" + teleportHit + ", unread=" + unread + "]"; }
}

    /** 图标、文字和点击范围共用底行中心；传送的纵向点击区不能跨到名称或时间上。 */
    public static Card card(int width, int nameHeight, int previewHeight, int timeWidth,
                            int unreadWidth, boolean canTeleport) {
        int h = cardHeight(nameHeight, previewHeight);
        int frameSize = AVATAR_SIZE + AVATAR_FRAME * 2;
        Rect frame = new Rect(CARD_PAD, (h - frameSize) / 2, frameSize, frameSize);
        int textX = frame.x() + frame.width() + AVATAR_TEXT_GAP;
        int footerH = Math.max(previewHeight, UNREAD_HEIGHT);
        int nameY = (h - nameHeight - 1 - footerH) / 2;
        int footerY = nameY + nameHeight + 1;
        int timeX = width - CARD_PAD - timeWidth;
        Rect unread = new Rect(width - CARD_PAD - unreadWidth,
                footerY + (footerH - UNREAD_HEIGHT) / 2, unreadWidth, UNREAD_HEIGHT);
        int previewRight = unreadWidth > 0 ? unread.x() - CARD_ACTION_GAP : width - CARD_PAD;
        Rect tpHit = new Rect(0, 0, 0, 0);
        float tpX = 0, tpY = 0;
        if (canTeleport) {
            int hitW = TELEPORT_ICON_SIZE + TELEPORT_HIT_PAD * 2;
            tpHit = new Rect(previewRight - hitW, footerY, hitW, footerH);
            tpX = tpHit.x() + TELEPORT_HIT_PAD;
            tpY = footerY + (footerH - TELEPORT_ICON_SIZE) / 2f;
            previewRight = tpHit.x() - CARD_ACTION_GAP;
        }
        int nameRight = timeWidth > 0 ? timeX - CARD_ACTION_GAP : width - CARD_PAD;
        return new Card(h, frame, textX, nameY, Math.max(0, nameRight - textX),
                timeX, nameY + (nameHeight - previewHeight) / 2f,
                footerY + (footerH - previewHeight) / 2f, Math.max(0, previewRight - textX),
                tpX, tpY, tpHit, unread);
    }

    public static int visibleCards(int availableHeight, int cardHeight) {
        return Math.max(1, (availableHeight + CARD_GAP) / (cardHeight + CARD_GAP));
    }

    /** 上方留昵称槽；短气泡底边对齐头像，长内容从同一头像行向下展开。 */
    public static MessageRow messageRow(int contentHeight, int nameLineHeight) {
        int avatarY = nameLineHeight + 1;
        int contentY = avatarY + Math.max(0, AVATAR_SIZE - contentHeight);
        return new MessageRow(avatarY, contentY, avatarY + Math.max(AVATAR_SIZE, contentHeight));
    }

    public static final class MessageRow {
        private final int avatarY;
        private final int contentY;
        private final int height;

        public MessageRow(int avatarY, int contentY, int height) {
            this.avatarY = avatarY;
            this.contentY = contentY;
            this.height = height;
        }

        public int avatarY() { return avatarY; }

        public int contentY() { return contentY; }

        public int height() { return height; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MessageRow)) return false;
            MessageRow that = (MessageRow) other;
            return this.avatarY == that.avatarY
                    && this.contentY == that.contentY
                    && this.height == that.height;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(avatarY);
            hash = 31 * hash + java.util.Objects.hashCode(contentY);
            hash = 31 * hash + java.util.Objects.hashCode(height);
            return hash;
        }

        @Override public String toString() { return "MessageRow[avatarY=" + avatarY + ", contentY=" + contentY + ", height=" + height + "]"; }
}

    /** 群聊按头像、昵称、气泡组成一行；头像从行顶开始，昵称紧贴正文而不是悬在头像上方。 */
    public static MessageRow groupMessageRow(int contentHeight, int nameLineHeight) {
        int contentY = GROUP_NAME_TOP + nameLineHeight + GROUP_NAME_GAP;
        return new MessageRow(0, contentY, Math.max(AVATAR_SIZE, contentY + contentHeight));
    }

    public static int messageContentWidth(int rowWidth) {
        return Math.max(0, rowWidth - 2 * (AVATAR_SIZE + MESSAGE_AVATAR_GAP));
    }

    /** 正文绘制、选字与右键命中共用同一列坐标。 */
    public static int messageContentX(int rowWidth, int contentWidth, boolean self) {
        return self ? rowWidth - AVATAR_SIZE - MESSAGE_AVATAR_GAP - contentWidth : AVATAR_SIZE + MESSAGE_AVATAR_GAP;
    }

    /** 收到的昵称靠气泡左边，发出的昵称靠气泡右边；短消息不会强制把昵称截成气泡宽度。 */
    public static TextOrigin groupName(int rowWidth, int nativeNameWidth, boolean self) {
        float x = self ? rowWidth - AVATAR_SIZE - MESSAGE_AVATAR_GAP - nativeNameWidth * GROUP_NAME_SCALE
                : AVATAR_SIZE + MESSAGE_AVATAR_GAP;
        return new TextOrigin(x, GROUP_NAME_TOP);
    }

    /**
     * 多行正文整体居中，各行仍左对齐。原版字宽包含行尾间距，中文字形又比旧的
     * 七像素估计高；统一去掉行尾间距并补偿半像素上方笔画偏移，避免正文偏左、偏下。
     * 这是默认字体的光学校准，资源包自定义字体仍需实测。
     */
    public static TextOrigin bubbleText(int width, int height, int maxAdvance, int fontHeight, int lines) {
        float inkWidth = Math.max(0, maxAdvance - 1) * TEXT_SCALE;
        int lineStep = scaledWidth(fontHeight, TEXT_SCALE);
        float inkHeight = Math.max(0, lines - 1) * lineStep + Math.max(1, fontHeight - 1) * TEXT_SCALE;
        return new TextOrigin((width - inkWidth) / 2f, (height - inkHeight) / 2f - TEXT_SCALE / 2f);
    }

    public static final class TextOrigin {
        private final float x;
        private final float y;

        public TextOrigin(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public float x() { return x; }

        public float y() { return y; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TextOrigin)) return false;
            TextOrigin that = (TextOrigin) other;
            return Float.compare(this.x, that.x) == 0
                    && Float.compare(this.y, that.y) == 0;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(x);
            hash = 31 * hash + java.util.Objects.hashCode(y);
            return hash;
        }

        @Override public String toString() { return "TextOrigin[x=" + x + ", y=" + y + "]"; }
}

    /** 右边和下边不包含在命中区内，贴近的两个控件不能同时接到边界上的点击。 */
    public static boolean hit(double mx, double my, int x, int y, int width, int height) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
}
