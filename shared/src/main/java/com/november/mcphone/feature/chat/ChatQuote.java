package com.november.mcphone.feature.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import java.util.UUID;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** 服务端生成的单层引用快照；不递归保留原消息的引用，也不保存图片像素。 */
public final class ChatQuote {
    private final UUID message;
    private final UUID sender;
    private final String name;
    private final String text;
    private final boolean image;

    public UUID message() { return message; }

    public UUID sender() { return sender; }

    public String name() { return name; }

    public String text() { return text; }

    public boolean image() { return image; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChatQuote)) return false;
        ChatQuote that = (ChatQuote) other;
        return java.util.Objects.equals(this.message, that.message)
                && java.util.Objects.equals(this.sender, that.sender)
                && java.util.Objects.equals(this.name, that.name)
                && java.util.Objects.equals(this.text, that.text)
                && this.image == that.image;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(message);
        hash = 31 * hash + java.util.Objects.hashCode(sender);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(image);
        return hash;
    }

    @Override public String toString() { return "ChatQuote[message=" + message + ", sender=" + sender + ", name=" + name + ", text=" + text + ", image=" + image + "]"; }

    public static final int NAME_LIMIT = 64, TEXT_LIMIT = TextBody.MAX_LENGTH;
    public ChatQuote(UUID message, UUID sender, String name, String text, boolean image) {
        Objects.requireNonNull(message); Objects.requireNonNull(sender);
        if (name == null || name.length() > NAME_LIMIT || text == null || text.length() > TEXT_LIMIT)
            throw new IllegalArgumentException("引用快照超限");
        this.message = message;
        this.sender = sender;
        this.name = name;
        this.text = text;
        this.image = image;
    }
    public static ChatQuote from(ChatMessage original, String name) {
        String safeName = com.november.mcphone.util.TextSanitizer.sanitize(name, NAME_LIMIT);
        Object tMatch;
        String text = (tMatch = original.body()) instanceof TextBody ? ((TextBody) tMatch).text() : "";
        text = com.november.mcphone.util.TextSanitizer.sanitize(text.replace('\n', ' ').replace('\r', ' '), TEXT_LIMIT);
        return new ChatQuote(original.id(), original.sender(), safeName, text, original.body() instanceof ImageBody);
    }
    public static final Codec<ChatQuote> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("message").forGetter(ChatQuote::message),
        UUIDUtil.CODEC.fieldOf("sender").forGetter(ChatQuote::sender),
        bounded(NAME_LIMIT).fieldOf("name").forGetter(ChatQuote::name),
        bounded(TEXT_LIMIT).fieldOf("text").forGetter(ChatQuote::text),
        Codec.BOOL.fieldOf("image").forGetter(ChatQuote::image)).apply(i, ChatQuote::new));

    /** DFU 6 的 optionalFieldOf 会吞掉坏字段；四个平台都明确区分“缺失”和“损坏”。 */
    public static final MapCodec<Optional<ChatQuote>> OPTIONAL_FIELD = new MapCodec<Optional<ChatQuote>>() {
        public <T> DataResult<Optional<ChatQuote>> decode(DynamicOps<T> ops, MapLike<T> input) {
            T value = input.get("quote");
            return value == null ? DataResult.success(Optional.empty()) : CODEC.parse(ops,value).map(Optional::of);
        }
        public <T> RecordBuilder<T> encode(Optional<ChatQuote> value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return value.isPresent() ? prefix.add("quote",CODEC.encodeStart(ops,value.get())) : prefix;
        }
        public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.of(ops.createString("quote")); }
    };

    private static Codec<String> bounded(int limit) {
        // DFU 6（Forge 1.20.1）没有 Codec.validate，使用各目标共通的双向校验入口。
        java.util.function.Function<String,DataResult<String>> check = value -> value.length() <= limit
                ? DataResult.success(value) : DataResult.error(() -> "引用字段超过 " + limit + " 字符");
        return Codec.STRING.flatXmap(check,check);
    }
}
