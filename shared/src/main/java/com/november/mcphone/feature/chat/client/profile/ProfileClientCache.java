package com.november.mcphone.feature.chat.client.profile;

import com.november.mcphone.feature.chat.profile.net.ProfileWire;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 客户端主线程的小型票据收件箱；关闭页和断线后不接受过期资料回声。 */
public final class ProfileClientCache {
    private ProfileClientCache() {}
    public static final long REQUEST_TIMEOUT_MS = 6_000L;
    public static final int MAX_PENDING = 8;
    private static final class Pending {
        private final UUID target;
        private final long deadline;

        private Pending(UUID target, long deadline) {
            this.target = target;
            this.deadline = deadline;
        }

        public UUID target() { return target; }

        public long deadline() { return deadline; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Pending)) return false;
            Pending that = (Pending) other;
            return java.util.Objects.equals(this.target, that.target)
                    && this.deadline == that.deadline;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(target);
            hash = 31 * hash + java.util.Objects.hashCode(deadline);
            return hash;
        }

        @Override public String toString() { return "Pending[target=" + target + ", deadline=" + deadline + "]"; }
}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, ProfileWire.Reply> RESULTS = new HashMap<>();
    public static UUID begin(UUID target, long now) {
        PENDING.entrySet().removeIf(e -> e.getValue().deadline() <= now);
        RESULTS.keySet().retainAll(PENDING.keySet());
        if (PENDING.size() >= MAX_PENDING) throw new IllegalStateException("资料请求过多");
        UUID id = UUID.randomUUID(); PENDING.put(id, new Pending(target, now + REQUEST_TIMEOUT_MS)); return id;
    }
    public static void accept(ProfileWire.Reply reply) { accept(reply, System.currentTimeMillis()); }
    public static void accept(ProfileWire.Reply reply, long now) {
        ProfileClientCache.Pending p = PENDING.get(reply.request());
        if (p != null && p.deadline() > now && p.target().equals(reply.target())) RESULTS.putIfAbsent(reply.request(), reply);
    }
    public static ProfileWire.Reply consume(UUID id) {
        com.november.mcphone.feature.chat.profile.net.ProfileWire.Reply r = RESULTS.remove(id); if (r != null) PENDING.remove(id); return r;
    }
    public static void cancel(UUID id) { PENDING.remove(id); RESULTS.remove(id); }
    public static void clear() { PENDING.clear(); RESULTS.clear(); }
}
