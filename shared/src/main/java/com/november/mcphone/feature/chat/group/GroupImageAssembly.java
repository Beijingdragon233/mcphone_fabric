package com.november.mcphone.feature.chat.group;

import com.november.mcphone.feature.chat.ChatImage;
import com.november.mcphone.feature.chat.group.net.GroupCommand;
import java.util.Arrays;
import java.util.UUID;
import java.util.WeakHashMap;

/** 每条连接仅一份群上传；群 ID、独立票据、元数据和顺序必须全匹配。 */
public final class GroupImageAssembly {
    public static final class Complete {
        private final GroupCommand.Upload header;
        private final byte[] png;

        public GroupCommand.Upload header() { return header; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Complete)) return false;
            Complete that = (Complete) other;
            return java.util.Objects.equals(this.header, that.header)
                    && java.util.Objects.equals(this.png, that.png);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(header);
            hash = 31 * hash + java.util.Objects.hashCode(png);
            return hash;
        }

        @Override public String toString() { return "Complete[header=" + header + ", png=" + png + "]"; }

        public Complete(GroupCommand.Upload header, byte[] png) { png = png.clone();
            this.header = header;
            this.png = png;
        }
        public byte[] png() { return png.clone(); }
    }
    private static final class Session {
        final GroupCommand.Upload header;
        final byte[] bytes;
        final long started;
        int size, next;
        Session(GroupCommand.Upload header, int limit, long now) { this.header = header; bytes = new byte[limit]; started = now; }
    }
    private final WeakHashMap<Object, Session> pending = new WeakHashMap<>();
    public Complete accept(Object connection, GroupCommand.Upload chunk, int limit, long now) {
        if (chunk.width() < 1 || chunk.width() > ChatImage.MAX_SIDE || chunk.height() < 1 || chunk.height() > ChatImage.MAX_SIDE
                || chunk.frames() < 1 || chunk.frames() > ChatImage.MAX_FRAMES
                || (chunk.frames() > 1 && (chunk.frameMs() < 20 || chunk.frameMs() > 5000))
                || limit < 1 || limit > ChatImage.MAX_BYTES_CEILING) { pending.remove(connection); return null; }
        if (chunk.index() == 0) pending.put(connection, new Session(chunk, limit, now));
        GroupImageAssembly.Session session = pending.get(connection); if (session == null) return null;
        GroupCommand.Upload first = session.header; byte[] data = chunk.data();
        if (!first.group().equals(chunk.group()) || !first.request().equals(chunk.request()) || first.count() != chunk.count()
                || first.width() != chunk.width() || first.height() != chunk.height() || first.frames() != chunk.frames()
                || first.frameMs() != chunk.frameMs() || chunk.index() != session.next || now - session.started > ChatImage.UPLOAD_TIMEOUT_MS
                || now < session.started || data.length == 0 || session.size + data.length > session.bytes.length) {
            pending.remove(connection); return null;
        }
        System.arraycopy(data, 0, session.bytes, session.size, data.length); session.size += data.length; session.next++;
        if (session.next < first.count()) return null;
        pending.remove(connection); return new Complete(first, Arrays.copyOf(session.bytes, session.size));
    }
}
