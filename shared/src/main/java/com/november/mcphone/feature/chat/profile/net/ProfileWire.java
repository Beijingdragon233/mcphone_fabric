package com.november.mcphone.feature.chat.profile.net;

import com.november.mcphone.feature.chat.profile.ProfileBook;
import net.minecraft.network.FriendlyByteBuf;
import java.util.Objects;
import java.util.UUID;

/** 小型、限长的资料协议；空介绍也是合法修改，用独立标志区分读取请求。 */
public final class ProfileWire {
    private ProfileWire() {}
    public enum Status {
        OK(0), FORBIDDEN(1), INVALID(2), BUSY(3), UNAVAILABLE(4);
        private final int wireId;
        Status(int wireId) { this.wireId = wireId; }
        public int wireId() { return wireId; }
        public static Status fromWire(int id) {
            for (ProfileWire.Status status : values()) if (status.wireId == id) return status;
            throw new io.netty.handler.codec.DecoderException("非法资料状态 " + id);
        }
    }
    public static final int NAME_LENGTH = 64;
    public static final class Query {
        private final UUID request;
        private final UUID target;
        private final boolean update;
        private final String introduction;

        public UUID request() { return request; }

        public UUID target() { return target; }

        public boolean update() { return update; }

        public String introduction() { return introduction; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Query)) return false;
            Query that = (Query) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.target, that.target)
                    && this.update == that.update
                    && java.util.Objects.equals(this.introduction, that.introduction);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(target);
            hash = 31 * hash + java.util.Objects.hashCode(update);
            hash = 31 * hash + java.util.Objects.hashCode(introduction);
            return hash;
        }

        @Override public String toString() { return "Query[request=" + request + ", target=" + target + ", update=" + update + ", introduction=" + introduction + "]"; }

        public Query(UUID request, UUID target, boolean update, String introduction) { Objects.requireNonNull(request); Objects.requireNonNull(target); Objects.requireNonNull(introduction);
            if (introduction.length() > ProfileBook.MAX_INTRO || !update && !introduction.isEmpty()) throw new IllegalArgumentException("无效资料请求");
            this.request = request;
            this.target = target;
            this.update = update;
            this.introduction = introduction;
        }
    }
    public static final class Reply {
        private final UUID request;
        private final UUID target;
        private final Status status;
        private final String name;
        private final String introduction;

        public UUID request() { return request; }

        public UUID target() { return target; }

        public Status status() { return status; }

        public String name() { return name; }

        public String introduction() { return introduction; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Reply)) return false;
            Reply that = (Reply) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.target, that.target)
                    && java.util.Objects.equals(this.status, that.status)
                    && java.util.Objects.equals(this.name, that.name)
                    && java.util.Objects.equals(this.introduction, that.introduction);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(target);
            hash = 31 * hash + java.util.Objects.hashCode(status);
            hash = 31 * hash + java.util.Objects.hashCode(name);
            hash = 31 * hash + java.util.Objects.hashCode(introduction);
            return hash;
        }

        @Override public String toString() { return "Reply[request=" + request + ", target=" + target + ", status=" + status + ", name=" + name + ", introduction=" + introduction + "]"; }

        public Reply(UUID request, UUID target, Status status, String name, String introduction) { Objects.requireNonNull(request); Objects.requireNonNull(target); Objects.requireNonNull(status);
            Objects.requireNonNull(name); Objects.requireNonNull(introduction);
            if (name.length() > NAME_LENGTH || introduction.length() > ProfileBook.MAX_INTRO) throw new IllegalArgumentException("资料超限");
            this.request = request;
            this.target = target;
            this.status = status;
            this.name = name;
            this.introduction = introduction;
        }
    }
    public static void writeQuery(Query q, FriendlyByteBuf b) {
        b.writeUUID(q.request()); b.writeUUID(q.target()); b.writeBoolean(q.update()); b.writeUtf(q.introduction(), ProfileBook.MAX_INTRO);
    }
    public static Query readQuery(FriendlyByteBuf b) { return new Query(b.readUUID(), b.readUUID(), b.readBoolean(), b.readUtf(ProfileBook.MAX_INTRO)); }
    public static void writeReply(Reply r, FriendlyByteBuf b) {
        b.writeUUID(r.request()); b.writeUUID(r.target()); b.writeVarInt(r.status().wireId()); b.writeUtf(r.name(), NAME_LENGTH); b.writeUtf(r.introduction(), ProfileBook.MAX_INTRO);
    }
    public static Reply readReply(FriendlyByteBuf b) { return new Reply(b.readUUID(), b.readUUID(), Status.fromWire(b.readVarInt()), b.readUtf(NAME_LENGTH), b.readUtf(ProfileBook.MAX_INTRO)); }
}
