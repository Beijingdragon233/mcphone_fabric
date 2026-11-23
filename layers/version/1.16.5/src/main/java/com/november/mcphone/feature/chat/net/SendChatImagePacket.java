package com.november.mcphone.feature.chat.net;

import com.november.mcphone.core.net.Wire;
import com.november.mcphone.feature.chat.ChatImage;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * C2S：发一张图片里的一片。
 *
 * 为什么一张图要发好几个包
 *
 * 原版对客户端发上来的自定义包有 32767 字节的硬上限，而一张压过的照片常在 30～80 KB。
 * 于是切成 {@link ChatImage#CHUNK_BYTES} 一片按序发，服务端拼回去（见 ChatImageUploads）。
 *
 * 为什么每一片都带着收件人、宽高与帧信息
 *
 * 服务端要拿它们与本次上传的第一片核对：对不上就整次作废。多带这几十个字节，
 * 换来的是"服务端不必相信后续几片仍属于同一张图"。
 *
 * width/height 是【一帧】的大小。动图的字节是所有帧拼成的一张雪碧图（见 ChatImage），
 * 比一帧大好几倍；而收件人排版时要知道的是一帧多大。frames = 1 就是普通静态图。
 *
 * 图片 id 不在这里：那是服务端存下来之后才有的，让客户端指定等于允许它覆盖别人的图。
 */
public final class SendChatImagePacket {
    private final UUID target;
    private final int width;
    private final int height;
    private final int frames;
    private final int frameMs;
    private final int chunkIndex;
    private final int chunkCount;
    private final byte[] chunk;

    public SendChatImagePacket(UUID target, int width, int height, int frames, int frameMs, int chunkIndex, int chunkCount, byte[] chunk) {
        this.target = target;
        this.width = width;
        this.height = height;
        this.frames = frames;
        this.frameMs = frameMs;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.chunk = chunk;
    }

    public UUID target() { return target; }

    public int width() { return width; }

    public int height() { return height; }

    public int frames() { return frames; }

    public int frameMs() { return frameMs; }

    public int chunkIndex() { return chunkIndex; }

    public int chunkCount() { return chunkCount; }

    public byte[] chunk() { return chunk; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SendChatImagePacket)) return false;
        SendChatImagePacket that = (SendChatImagePacket) other;
        return java.util.Objects.equals(this.target, that.target)
                && this.width == that.width
                && this.height == that.height
                && this.frames == that.frames
                && this.frameMs == that.frameMs
                && this.chunkIndex == that.chunkIndex
                && this.chunkCount == that.chunkCount
                && java.util.Objects.equals(this.chunk, that.chunk);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(target);
        hash = 31 * hash + java.util.Objects.hashCode(width);
        hash = 31 * hash + java.util.Objects.hashCode(height);
        hash = 31 * hash + java.util.Objects.hashCode(frames);
        hash = 31 * hash + java.util.Objects.hashCode(frameMs);
        hash = 31 * hash + java.util.Objects.hashCode(chunkIndex);
        hash = 31 * hash + java.util.Objects.hashCode(chunkCount);
        hash = 31 * hash + java.util.Objects.hashCode(chunk);
        return hash;
    }

    @Override public String toString() { return "SendChatImagePacket[target=" + target + ", width=" + width + ", height=" + height + ", frames=" + frames + ", frameMs=" + frameMs + ", chunkIndex=" + chunkIndex + ", chunkCount=" + chunkCount + ", chunk=" + chunk + "]"; }


    public static void encode(SendChatImagePacket value, FriendlyByteBuf buf) {
        buf.writeUUID(value.target());
        buf.writeVarInt(value.width());
        buf.writeVarInt(value.height());
        buf.writeVarInt(value.frames());
        buf.writeVarInt(value.frameMs());
        buf.writeVarInt(value.chunkIndex());
        buf.writeVarInt(value.chunkCount());
        Wire.writeBytes(buf, value.chunk(), ChatImage.CHUNK_BYTES);
    }

    public static SendChatImagePacket decode(FriendlyByteBuf buf) {
        UUID target = buf.readUUID();
        int width = buf.readVarInt();
        int height = buf.readVarInt();
        int frames = buf.readVarInt();
        int frameMs = buf.readVarInt();
        int chunkIndex = buf.readVarInt();
        int chunkCount = buf.readVarInt();
        // 超长的一片在解码阶段就被拒收，轮不到业务层；发的那一侧同样拦，见 Wire
        byte[] chunk = Wire.readBytes(buf, ChatImage.CHUNK_BYTES);
        return new SendChatImagePacket(target, width, height, frames, frameMs,
                chunkIndex, chunkCount, chunk);
    }
}
