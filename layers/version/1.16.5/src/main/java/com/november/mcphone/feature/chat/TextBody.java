package com.november.mcphone.feature.chat;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** 玩家打的一段字。text 存原样，清洗在收包时做（见 ChatService.sendMessage）。 */
public final class TextBody implements MessageBody {
    private final String text;
    private final java.util.Optional<ChatQuote> quote;

    public String text() { return text; }

    public java.util.Optional<ChatQuote> quote() { return quote; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TextBody)) return false;
        TextBody that = (TextBody) other;
        return java.util.Objects.equals(this.text, that.text)
                && java.util.Objects.equals(this.quote, that.quote);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(quote);
        return hash;
    }

    @Override public String toString() { return "TextBody[text=" + text + ", quote=" + quote + "]"; }

    public TextBody(String text) { this(text, java.util.Optional.empty()); }
    public TextBody(String text, java.util.Optional<ChatQuote> quote) { java.util.Objects.requireNonNull(text); java.util.Objects.requireNonNull(quote);
        this.text = text;
        this.quote = quote;
    }

    /** 单条消息长度上限，在编解码器层面封死 */
    public static final int MAX_LENGTH = 256;

    public static final MapCodec<TextBody> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    com.mojang.serialization.Codec.STRING.fieldOf("text").forGetter(TextBody::text),
                    ChatQuote.OPTIONAL_FIELD.forGetter(TextBody::quote)
            ).apply(instance, TextBody::new)
    );

    /** 超长文本在解码阶段就被拒收，轮不到业务层 */
    public static void encode(TextBody body, FriendlyByteBuf buf) {
        buf.writeUtf(body.text(), MAX_LENGTH);
        com.november.mcphone.feature.chat.net.ChatReplyWire.write(buf,body.quote());
    }

    public static TextBody decode(FriendlyByteBuf buf) {
        return new TextBody(buf.readUtf(MAX_LENGTH), com.november.mcphone.feature.chat.net.ChatReplyWire.read(buf));
    }

    @Override
    public MessageKind kind() {
        return MessageKind.TEXT;
    }

    @Override
    public Component preview() {
        return Component.literal(text);
    }
}
