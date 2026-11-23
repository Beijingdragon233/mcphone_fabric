package com.november.mcphone.feature.chat.client;

import com.google.common.collect.ImmutableList;

import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.ImageBody;
import com.november.mcphone.feature.chat.TextBody;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 消息快照到排版块的转换；不发包、不操作输入控件、不保存滚动状态。 */
final class ChatMessageLayout {
    private ChatMessageLayout() {}
    static final int BLOCK_GAP = 5;
    static final int STAMP_PAD_Y = 2;
    private static final float BUBBLE_MAX_RATIO = 0.72f;
    private static final int BUBBLE_PAD_X = 3;
    private static final int BUBBLE_PAD_Y = 2;
    private static final long STAMP_GAP_MS = 5 * 60 * 1000L;
    private static final int IMAGE_MAX_H = 56;
    private static final int AVATAR_SIZE = ChatLayout.AVATAR_SIZE;
    private static final int AVATAR_GAP = ChatLayout.MESSAGE_AVATAR_GAP;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    enum BlockType {
        /** 居中的时间戳行，不画气泡 */
        STAMP,
        /** 文字气泡 */
        TEXT,
        /** 一张图，不套文字气泡 */
        IMAGE
    }

    /**
     * 排版后的一块。文字块的 w/h 含气泡内边距，图片块就是图本身的大小；
     * lines 只有文字块用得上，image 只有图片块用得上。
     *
     * 图片块为什么在这一步就把尺寸算好：像素是"看到了才去要"的（见 {@link ChatImageCache}），
     * 拿到之前也得把那块地方占出来，尺寸按消息自带的宽高算（见 ImageBody）。等图到了再按真实
     * 比例重排的话，那一下跳动恰好发生在玩家正看着的地方。
     *
     * frames/frameMs 也从消息上抄过来（静态图是 1 和 0）：像素里看不出一张图是不是动图，
     * 而画的时候要靠它挑帧。
     */
    static final class Block {
        private final BlockType type;
        private final boolean self;
        private final List<FormattedCharSequence> lines;
        private final UUID image;
        private final int w;
        private final int h;
        private final int frames;
        private final int frameMs;
        private final ChatLayout.MessageRow placement;
        private final ChatLayout.TextOrigin textOrigin;
        private final ChatMessage message;
        private final ChatTextLayout text;

        Block(BlockType type, boolean self, List<FormattedCharSequence> lines, UUID image, int w, int h, int frames, int frameMs, ChatLayout.MessageRow placement, ChatLayout.TextOrigin textOrigin, ChatMessage message, ChatTextLayout text) {
            this.type = type;
            this.self = self;
            this.lines = lines;
            this.image = image;
            this.w = w;
            this.h = h;
            this.frames = frames;
            this.frameMs = frameMs;
            this.placement = placement;
            this.textOrigin = textOrigin;
            this.message = message;
            this.text = text;
        }

        public BlockType type() { return type; }

        public boolean self() { return self; }

        public List<FormattedCharSequence> lines() { return lines; }

        public UUID image() { return image; }

        public int w() { return w; }

        public int h() { return h; }

        public int frames() { return frames; }

        public int frameMs() { return frameMs; }

        public ChatLayout.MessageRow placement() { return placement; }

        public ChatLayout.TextOrigin textOrigin() { return textOrigin; }

        public ChatMessage message() { return message; }

        public ChatTextLayout text() { return text; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Block)) return false;
            Block that = (Block) other;
            return java.util.Objects.equals(this.type, that.type)
                    && this.self == that.self
                    && java.util.Objects.equals(this.lines, that.lines)
                    && java.util.Objects.equals(this.image, that.image)
                    && this.w == that.w
                    && this.h == that.h
                    && this.frames == that.frames
                    && this.frameMs == that.frameMs
                    && java.util.Objects.equals(this.placement, that.placement)
                    && java.util.Objects.equals(this.textOrigin, that.textOrigin)
                    && java.util.Objects.equals(this.message, that.message)
                    && java.util.Objects.equals(this.text, that.text);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(type);
            hash = 31 * hash + java.util.Objects.hashCode(self);
            hash = 31 * hash + java.util.Objects.hashCode(lines);
            hash = 31 * hash + java.util.Objects.hashCode(image);
            hash = 31 * hash + java.util.Objects.hashCode(w);
            hash = 31 * hash + java.util.Objects.hashCode(h);
            hash = 31 * hash + java.util.Objects.hashCode(frames);
            hash = 31 * hash + java.util.Objects.hashCode(frameMs);
            hash = 31 * hash + java.util.Objects.hashCode(placement);
            hash = 31 * hash + java.util.Objects.hashCode(textOrigin);
            hash = 31 * hash + java.util.Objects.hashCode(message);
            hash = 31 * hash + java.util.Objects.hashCode(text);
            return hash;
        }

        @Override public String toString() { return "Block[type=" + type + ", self=" + self + ", lines=" + lines + ", image=" + image + ", w=" + w + ", h=" + h + ", frames=" + frames + ", frameMs=" + frameMs + ", placement=" + placement + ", textOrigin=" + textOrigin + ", message=" + message + ", text=" + text + "]"; }

        int quoteHeight() { Object tMatch;
        return message != null && (tMatch = message.body()) instanceof TextBody && ((TextBody) tMatch).quote().isPresent() ? ChatReplyView.HEIGHT : 0; }
        int rowHeight() { return placement.height(); }
    }

    static List<Block> build(Font font, List<ChatMessage> src, int maxW, UUID selfId) {
        return build(font, src, maxW, selfId, false);
    }

    static List<Block> build(Font font, List<ChatMessage> src, int maxW, UUID selfId, boolean group) {
        final int bubbleMaxW = Math.max(24, Math.min((int) (maxW * BUBBLE_MAX_RATIO),
                maxW - AVATAR_SIZE - AVATAR_GAP - 12));
        final int textMaxW = ChatLayout.unscaledWidth(bubbleMaxW - BUBBLE_PAD_X * 2,
                ChatLayout.TEXT_SCALE);
        final int nameLineHeight = ChatUi.lineHeight(font, ChatLayout.META_SCALE);
        final int senderLineHeight = ChatUi.lineHeight(font, ChatLayout.GROUP_NAME_SCALE);

        List<Block> out = new ArrayList<>();
        long prevTime = 0L;

        for (ChatMessage m : src) {
            if (m.time() - prevTime > STAMP_GAP_MS) {
                // 时间格式只有数字与分隔符，不需要初始化客户端语言或双向排版。
                FormattedCharSequence stamp = FormattedCharSequence.forward(formatStamp(m.time()), Style.EMPTY);
                int stampH = nameLineHeight + STAMP_PAD_Y * 2;
                out.add(new Block(BlockType.STAMP, false, ImmutableList.of(stamp), null,
                        ChatLayout.scaledWidth(font.width(stamp), ChatLayout.META_SCALE),
                        stampH, 1, 0, new ChatLayout.MessageRow(0, 0, stampH), null, null, null));
            }
            prevTime = m.time();

            boolean self = selfId != null && selfId.equals(m.sender());

            Object imageMatch;
            if ((imageMatch = m.body()) instanceof ImageBody) {
                out.add(imageBlock(m, ((ImageBody) imageMatch), self, bubbleMaxW, nameLineHeight, senderLineHeight, group));
                continue;
            }

            Object tMatch_;
            String text = (tMatch_ = m.body()) instanceof TextBody ? ((TextBody) tMatch_).text() : m.body().preview().getString();
            ChatTextLayout selectable = ChatTextLayout.build(font, text, textMaxW);
            List<FormattedCharSequence> lines = selectable.visualLines();
            int textW = 0;
            for (net.minecraft.util.FormattedCharSequence line : lines) textW = Math.max(textW, font.width(line));

            int bubbleH = lines.size() * ChatUi.lineHeight(font, ChatLayout.TEXT_SCALE) + BUBBLE_PAD_Y * 2;
            int bubbleW = ChatLayout.scaledWidth(textW, ChatLayout.TEXT_SCALE) + BUBBLE_PAD_X * 2;
            Object tMatch__;
            int quoteH = (tMatch__ = m.body()) instanceof TextBody && ((TextBody) tMatch__).quote().isPresent() ? ChatReplyView.HEIGHT : 0;
            if (quoteH > 0) bubbleW = bubbleMaxW;
            ChatLayout.TextOrigin origin = ChatLayout.bubbleText(bubbleW,bubbleH,textW,font.lineHeight,lines.size());
            bubbleH += quoteH;
            out.add(new Block(BlockType.TEXT, self, lines, null,
                    bubbleW, bubbleH, 1, 0, placement(bubbleH, nameLineHeight, senderLineHeight, group),
                    new ChatLayout.TextOrigin(quoteH > 0 ? BUBBLE_PAD_X : origin.x(),origin.y()+quoteH), m, selectable));
        }

        return ImmutableList.copyOf(out);
    }

    private static ChatLayout.MessageRow placement(int height, int reservedNameHeight, int senderHeight, boolean group) {
        return group ? ChatLayout.groupMessageRow(height, senderHeight) : ChatLayout.messageRow(height, reservedNameHeight);
    }

    private static Block imageBlock(ChatMessage message, ImageBody image, boolean self, int bubbleMaxW,
                                    int nameLineHeight, int senderLineHeight, boolean group) {
        float scale = Math.min((float) bubbleMaxW / image.width(),
                               (float) IMAGE_MAX_H / image.height());
        // 比屏幕还小的图不放大：放大只会糊，而手机上的图本来就该小
        scale = Math.min(scale, 1f);

        int w = Math.max(1, Math.round(image.width() * scale));
        int h = Math.max(1, Math.round(image.height() * scale));

        return new Block(BlockType.IMAGE, self, ImmutableList.of(), image.image(), w, h,
                image.frames(), image.frameMs(), placement(h, nameLineHeight, senderLineHeight, group), null, message, null);
    }

    private static String formatStamp(long time) {
        java.time.ZoneId zone = ZoneId.systemDefault();
        java.time.ZonedDateTime dateTime = Instant.ofEpochMilli(time).atZone(zone);
        return dateTime.toLocalDate().equals(LocalDate.now(zone))
                ? dateTime.format(TIME_FORMAT)
                : dateTime.format(DATE_TIME_FORMAT);
    }
}
