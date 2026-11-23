package com.november.mcphone.feature.chat.client;

import com.november.mcphone.util.Java8;

import com.google.common.collect.ImmutableList;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.core.client.PhoneTheme;
import com.november.mcphone.core.client.PlayerAvatar;
import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.client.ChatMessageLayout.Block;
import com.november.mcphone.feature.chat.client.ChatMessageLayout.BlockType;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.november.mcphone.feature.chat.client.ChatMessageLayout.BLOCK_GAP;
import static com.november.mcphone.feature.chat.client.ChatMessageLayout.STAMP_PAD_Y;

/** 消息区拥有排版缓存、滚动量和可见图片命中区，状态跟随一个会话页实例。 */
final class ChatMessagePane {
    private static final int AVATAR_SIZE = ChatLayout.AVATAR_SIZE;
    private static final int COLOR_TEXT_SELF = PhoneTheme.FONT_COLOR_CHAT_SELF;
    private static final int COLOR_TEXT_PEER = PhoneTheme.FONT_COLOR_CHAT_PEER;
    private static int colorStamp() { return FontPalette.timestamp(); }
    private static int colorEmpty() { return FontPalette.subtle(); }
    private UUID peer;
    private java.util.function.Function<ChatMessage, String> senderName = message -> "";
    private UUID selfId;
    private UUID laidOutSelf;
    private Font laidOutFont;
    private List<ChatMessage> laidOutFrom;
    private int laidOutWidth = -1;
    private boolean group, laidOutGroup;
    private List<Block> blocks = ImmutableList.of();
    private final ChatScrollState scroll = new ChatScrollState();
    private final List<ImageHit> imageHits = new ArrayList<>();
    private int messageTop, messageBottom;
    private final ChatTextSelection selection = new ChatTextSelection();
    private final List<TextHit> textHits = new ArrayList<>();
    private final List<MessageHit> messageHits = new ArrayList<>();
    private static final class MessageHit {
        private final ChatMessage message;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private MessageHit(ChatMessage message, int x, int y, int width, int height) {
            this.message = message;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public ChatMessage message() { return message; }

        public int x() { return x; }

        public int y() { return y; }

        public int width() { return width; }

        public int height() { return height; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MessageHit)) return false;
            MessageHit that = (MessageHit) other;
            return java.util.Objects.equals(this.message, that.message)
                    && this.x == that.x
                    && this.y == that.y
                    && this.width == that.width
                    && this.height == that.height;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(message);
            hash = 31 * hash + java.util.Objects.hashCode(x);
            hash = 31 * hash + java.util.Objects.hashCode(y);
            hash = 31 * hash + java.util.Objects.hashCode(width);
            hash = 31 * hash + java.util.Objects.hashCode(height);
            return hash;
        }

        @Override public String toString() { return "MessageHit[message=" + message + ", x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]"; }
}
    private double dragX, dragY;
    private static final class TextHit {
        private final Block block;
        private final int x;
        private final int y;
        private final int lineStep;

        private TextHit(Block block, int x, int y, int lineStep) {
            this.block = block;
            this.x = x;
            this.y = y;
            this.lineStep = lineStep;
        }

        public Block block() { return block; }

        public int x() { return x; }

        public int y() { return y; }

        public int lineStep() { return lineStep; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TextHit)) return false;
            TextHit that = (TextHit) other;
            return java.util.Objects.equals(this.block, that.block)
                    && this.x == that.x
                    && this.y == that.y
                    && this.lineStep == that.lineStep;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(block);
            hash = 31 * hash + java.util.Objects.hashCode(x);
            hash = 31 * hash + java.util.Objects.hashCode(y);
            hash = 31 * hash + java.util.Objects.hashCode(lineStep);
            return hash;
        }

        @Override public String toString() { return "TextHit[block=" + block + ", x=" + x + ", y=" + y + ", lineStep=" + lineStep + "]"; }
}
    private static final class ImageHit {
        private final UUID image;
        private final int x;
        private final int y;
        private final int w;
        private final int h;

        private ImageHit(UUID image, int x, int y, int w, int h) {
            this.image = image;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public UUID image() { return image; }

        public int x() { return x; }

        public int y() { return y; }

        public int w() { return w; }

        public int h() { return h; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ImageHit)) return false;
            ImageHit that = (ImageHit) other;
            return java.util.Objects.equals(this.image, that.image)
                    && this.x == that.x
                    && this.y == that.y
                    && this.w == that.w
                    && this.h == that.h;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(image);
            hash = 31 * hash + java.util.Objects.hashCode(x);
            hash = 31 * hash + java.util.Objects.hashCode(y);
            hash = 31 * hash + java.util.Objects.hashCode(w);
            hash = 31 * hash + java.util.Objects.hashCode(h);
            return hash;
        }

        @Override public String toString() { return "ImageHit[image=" + image + ", x=" + x + ", y=" + y + ", w=" + w + ", h=" + h + "]"; }
}
    private static final class Nickname {
        private final String text;
        private final float x;
        private final float y;

        private Nickname(String text, float x, float y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }

        public String text() { return text; }

        public float x() { return x; }

        public float y() { return y; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Nickname)) return false;
            Nickname that = (Nickname) other;
            return java.util.Objects.equals(this.text, that.text)
                    && Float.compare(this.x, that.x) == 0
                    && Float.compare(this.y, that.y) == 0;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(text);
            hash = 31 * hash + java.util.Objects.hashCode(x);
            hash = 31 * hash + java.util.Objects.hashCode(y);
            return hash;
        }

        @Override public String toString() { return "Nickname[text=" + text + ", x=" + x + ", y=" + y + "]"; }
}

    void reset() {
        peer = selfId = laidOutSelf = null;
        laidOutFont = null;
        laidOutFrom = null;
        laidOutWidth = -1;
        group = laidOutGroup = false;
        senderName = message -> "";
        blocks = ImmutableList.of();
        imageHits.clear();
        textHits.clear();
        messageHits.clear();
        selection.clear();
        messageTop = messageBottom = 0;
        scroll.reset();
    }

    void latest() { scroll.latest(); }
    boolean mouseScrolled(double amount) { return scroll.scroll(amount); }
    void clearSelection() { selection.clear(); }
    String selectedText() { return selection.selectedText(); }
    String selectedText(UUID message) { return selection.owns(message) ? selection.selectedText() : ""; }
    void prepareMenu(UUID message) { selection.release(); if (!selection.owns(message)) selection.clear(); }
    ChatMessage messageAt(double mx, double my) {
        if (my < messageTop || my >= messageBottom) return null;
        for (ChatMessagePane.MessageHit hit : messageHits) {
            if (GuiUtil.hit(mx, my, hit.x(), hit.y(), hit.width(), hit.height())) return hit.message();
        }
        return null;
    }

    boolean mouseClicked(double mx, double my) {
        selection.clear();
        if (my < messageTop || my >= messageBottom) return false;
        for (TextHit hit : textHits) {
            Block b = hit.block();
            if (b.text().selectable() && GuiUtil.hit(mx, my, hit.x(), hit.y()+b.quoteHeight(), b.w(), b.h()-b.quoteHeight())) {
                selection.begin(b.message().id(), b.text(), indexAt(hit, mx, my));
                dragX = mx; dragY = my;
                return true;
            }
        }
        return false;
    }

    boolean mouseDragged(double mx, double my, int button) {
        if (button != 0 || !selection.dragging()) return false;
        dragX = mx; dragY = my;
        updateDrag();
        return true;
    }

    boolean mouseReleased(double mx, double my, int button) {
        if (button != 0 || !selection.dragging()) return false;
        mouseDragged(mx, my, button);
        return selection.release();
    }

    private void updateDrag() {
        for (TextHit hit : textHits) {
            if (selection.owns(hit.block().message().id())) {
                selection.extend(indexAt(hit, dragX, dragY));
                return;
            }
        }
    }

    private static int indexAt(TextHit hit, double mx, double my) {
        ChatLayout.TextOrigin origin = hit.block().textOrigin();
        return hit.block().text().indexAt((mx - hit.x() - origin.x()) / ChatLayout.TEXT_SCALE,
                my - hit.y() - origin.y(), hit.lineStep());
    }

    UUID imageAt(double mx, double my) {
        if (my < messageTop || my >= messageBottom) return null;
        for (ImageHit hit : imageHits) {
            if (GuiUtil.hit(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) return hit.image();
        }
        return null;
    }

    private void relayout(Font font, int width, List<ChatMessage> src, boolean group) {
        if (src == laidOutFrom && width == laidOutWidth && font == laidOutFont
                && Objects.equals(selfId, laidOutSelf) && group == laidOutGroup) return;
        selection.reconcile(src);
        blocks = ChatMessageLayout.build(font, src, width, selfId, group);
        int total = 0;
        for (Block b : blocks) total += b.rowHeight() + BLOCK_GAP;
        scroll.contentChanged(total);
        laidOutFrom = src;
        laidOutWidth = width;
        laidOutFont = font;
        laidOutSelf = selfId;
        laidOutGroup = group;
    }

    void render(GuiGraphics g, Font font, int x, int top, int w, int bottom, UUID peer, UUID selfId,
                boolean group, List<ChatMessage> source, java.util.function.Function<ChatMessage, String> names) {
        this.peer = peer;
        this.selfId = selfId;
        this.senderName = names;
        this.group = group;
        relayout(font, w, source, group);
        // 每帧重建：滚一下、来一条新消息，位置就全变了
        imageHits.clear();
        textHits.clear();
        messageHits.clear();
        messageTop = top;
        messageBottom = bottom;

        if (blocks.isEmpty()) {
            int y = top;
            for (net.minecraft.util.FormattedCharSequence line : font.split(Component.translatable("mcphone.chat.conversation_empty"),
                    ChatLayout.unscaledWidth(w, ChatLayout.TEXT_SCALE))) {
                ChatUi.text(g, font, line, x, y, ChatLayout.TEXT_SCALE, colorEmpty());
                y += ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE);
            }
            scroll.viewport(0);
            return;
        }

        final int viewH = bottom - top;
        scroll.viewport(viewH);

        // 不足一屏从顶往下排；超出时贴底，滚动偏移把内容往下推露出更早的消息
        int y = scroll.contentHeight() <= viewH ? top : bottom - scroll.contentHeight() + scroll.offset();

        // 走 GuiUtil 那一层：原版的 enableScissor 收窗口坐标、不跟随 pose，而整个手机是
        // 套在一层缩放里画的（界面大小 × 开机动画）。直接交本地坐标，界面大小一改字就被切
        GuiUtil.enableScissor(g, x, top, x + w, bottom);
        // 完整几何先算一遍，拖出可见区仍归原消息；滚动/新消息后不使用上一帧的命中区。
        layoutHits(x, y, w, ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE));
        if (selection.dragging()) updateDrag();
        for (Block b : blocks) {
            if (y + b.rowHeight() > top && y < bottom) renderBlock(g, font, b, x, y, w);
            y += b.rowHeight() + BLOCK_GAP;
        }
        GuiUtil.disableScissor(g);
    }

    private void renderBlock(GuiGraphics g, Font font, Block b, int x, int y, int w) {
        if (b.type() == BlockType.STAMP) {
            ChatUi.text(g, font, b.lines().get(0), x + (w - b.w()) / 2, y + STAMP_PAD_Y,
                    ChatLayout.META_SCALE, colorStamp());
            return;
        }

        UUID avatar = b.message().sender();
        int avatarY = y + b.placement().avatarY();
        int contentY = y + b.placement().contentY();
        // 群聊头像与昵称从同一行开始；私聊沿用原来的短气泡底边对齐。
        if (avatar != null) PlayerAvatar.draw(g, avatar,
                b.self() ? x + w - AVATAR_SIZE : x, avatarY, AVATAR_SIZE);
        int bx = bubbleX(b, x, w);
        ChatMessagePane.Nickname nickname = nickname(font, b, x, y, w);
        if (nickname != null) ChatUi.text(g, font, nickname.text(), nickname.x(), nickname.y(),
                ChatLayout.GROUP_NAME_SCALE, FontPalette.subtle());

        // 图片不套气泡：真实的聊天软件里图片就是图片本身，没有底色也没有一圈留白，
        // 谁发的靠左右对齐看得出来。套一层气泡等于在图周围多画一圈没有意义的色块，
        // 而手机屏幕只有 120 宽，那一圈还要从图身上扣
        if (b.type() == BlockType.IMAGE) {
            renderImageBlock(g, font, b, bx, contentY);
            return;
        }

        ChatUi.bubble(g, b.self(), bx, contentY, b.w(), b.h());

        Object textMatch;
        if ((textMatch = b.message().body()) instanceof com.november.mcphone.feature.chat.TextBody && ((com.november.mcphone.feature.chat.TextBody) textMatch).quote().isPresent())
            ChatReplyView.draw(g,font,((com.november.mcphone.feature.chat.TextBody) textMatch).quote().get(),bx+3,contentY,b.w()-6);
        renderSelection(g, font, b, bx, contentY);
        ChatLayout.TextOrigin origin = b.textOrigin();
        float ty = contentY + origin.y();
        for (net.minecraft.util.FormattedCharSequence line : b.lines()) {
            ChatUi.text(g, font, line, bx + origin.x(), ty, ChatLayout.TEXT_SCALE,
                    b.self() ? COLOR_TEXT_SELF : COLOR_TEXT_PEER);
            ty += ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE);
        }
    }

    private static int bubbleX(Block b, int x, int w) {
        return x + ChatLayout.messageContentX(w, b.w(), b.self());
    }

    private Nickname nickname(Font font, Block block, int x, int y, int width) {
        if (!group || block.type() == BlockType.STAMP) return null;
        String name = senderName.apply(block.message());
        int available = ChatLayout.messageContentWidth(width);
        if (name == null || Java8.isBlank(name) || available <= 0) return null;
        name = ChatUi.truncate(font, name, available, ChatLayout.GROUP_NAME_SCALE);
        ChatLayout.TextOrigin origin = ChatLayout.groupName(width, font.width(name), block.self());
        return new Nickname(name, x + origin.x(), y + origin.y());
    }

    /** 命中区按本帧排版重建；昵称不属于正文选区或右键消息区域。 */
    private void layoutHits(int x, int y, int width, int lineStep) {
        textHits.clear(); messageHits.clear();
        for (Block b : blocks) {
            int bx = bubbleX(b, x, width), by = y + b.placement().contentY();
            if (b.type() != BlockType.STAMP) messageHits.add(new MessageHit(b.message(), bx, by, b.w(), b.h()));
            if (b.type() == BlockType.TEXT) textHits.add(new TextHit(b, bx, by, lineStep));
            y += b.rowHeight() + BLOCK_GAP;
        }
    }

    private void renderSelection(GuiGraphics g, Font font, Block b, int bx, int y) {
        if (!selection.owns(b.message().id()) || selection.start() == selection.end()) return;
        ChatLayout.TextOrigin origin = b.textOrigin();
        int step = ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE);
        g.pose().pushPose();
        try {
            // 子像素矩形与正文共用原点，不在手机逻辑像素层提前取整。
            g.pose().translate(bx + origin.x(), y + origin.y(), 0);
            g.pose().scale(ChatLayout.TEXT_SCALE / 16f, ChatLayout.TEXT_SCALE / 16f, 1);
            for (int line = 0; line < b.text().lines().size(); line++) {
                for (ChatTextLayout.Span span : b.text().lines().get(line).spans(selection.start(), selection.end())) {
                    int top = Math.round(line * step / ChatLayout.TEXT_SCALE * 16f);
                    g.fill(Math.round(span.left() * 16f), top,
                            Math.round(span.right() * 16f), top + font.lineHeight * 16, PhoneTheme.COLOR_CHAT_SELECTION);
                    g.fill(Math.round(span.left() * 16f), top + font.lineHeight * 16 - 4,
                            Math.round(span.right() * 16f), top + font.lineHeight * 16, PhoneTheme.COLOR_CHAT_SELECTION_EDGE);
                }
            }
        } finally { g.pose().popPose(); }
    }

    private void renderImageBlock(GuiGraphics g, Font font, Block b, int bx, int y) {
        int w = b.w();
        int h = b.h();

        // 每帧都告知：条目被逐出后会被重建成一条空的，而放大图那一层只有一个 id
        ChatImageCache.declare(b.image(), b.frames(), b.frameMs());

        com.november.mcphone.core.client.ImageCodec.Texture texture = ChatImageCache.get(b.image());
        if (texture != null) {
            // 有像素才记位置：点一张还没到、或者已经过期的图，放大了也只是一块空白
            imageHits.add(new ImageHit(b.image(), bx, y, w, h));
            ChatImageViewer.drawImage(g, texture, b.image(), bx, y, w, h);
            return;
        }

        g.fill(bx, y, bx + w, y + h, PhoneTheme.COLOR_SCRIM);

        String hint;
        switch (ChatImageCache.status(b.image())) {
            case GONE:
                hint = Component.translatable("mcphone.chat.image_expired").getString();
                break;
            case BROKEN:
                hint = Component.translatable("mcphone.chat.image_broken_local").getString();
                break;
            case LOADING:
            case READY:
                hint = "…";
                break;
            default: throw new IncompatibleClassChangeError();
        }
        // 先截再居中：那一块窄的时候（一张竖图）「已过期」放不下，按截断前的宽度算会偏出去
        hint = GuiUtil.truncate(font, hint, w - 2);
        g.drawString(font, hint,
                bx + Math.max(0, (w - font.width(hint)) / 2),
                y + (h - font.lineHeight) / 2,
                colorEmpty(), false);
    }
}
