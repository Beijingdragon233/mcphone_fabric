package com.november.mcphone.feature.chat.group.net;

import com.google.common.collect.ImmutableList;

import java.util.HashSet;
import com.november.mcphone.feature.chat.ChatDeletionResult;
import com.november.mcphone.feature.chat.group.*;
import java.util.List;
import java.util.UUID;

/** 服务端群响应；纯数据，可由专用服务器安全加载。 */
public interface GroupReply {
    final class Notices implements GroupReply {
        private final List<GroupInviteNotice> invitations;

        public List<GroupInviteNotice> invitations() { return invitations; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Notices)) return false;
            Notices that = (Notices) other;
            return java.util.Objects.equals(this.invitations, that.invitations);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(invitations);
            return hash;
        }

        @Override public String toString() { return "Notices[invitations=" + invitations + "]"; }

        public Notices(List<GroupInviteNotice> invitations) { invitations = ImmutableList.copyOf(invitations); if (invitations.size() > GroupLimits.INVITATIONS_PER_PLAYER)
            throw new IllegalArgumentException("群通知超限");
            this.invitations = invitations;
        }
    }
    final class Directory implements GroupReply {
        private final List<GroupSummary> groups;

        public List<GroupSummary> groups() { return groups; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Directory)) return false;
            Directory that = (Directory) other;
            return java.util.Objects.equals(this.groups, that.groups);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(groups);
            return hash;
        }

        @Override public String toString() { return "Directory[groups=" + groups + "]"; }

        public Directory(List<GroupSummary> groups) { groups = ImmutableList.copyOf(groups); if (groups.size() > GroupLimits.GROUPS_PER_PLAYER) throw new IllegalArgumentException("群摘要超限");
            this.groups = groups;
        }
    }
    final class History implements GroupReply {
        private final UUID request;
        private final UUID group;
        private final long revision;
        private final long sequence;
        private final GroupInfo info;
        private final List<GroupMessage> messages;

        public UUID request() { return request; }

        public UUID group() { return group; }

        public long revision() { return revision; }

        public long sequence() { return sequence; }

        public GroupInfo info() { return info; }

        public List<GroupMessage> messages() { return messages; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof History)) return false;
            History that = (History) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group)
                    && this.revision == that.revision
                    && this.sequence == that.sequence
                    && java.util.Objects.equals(this.info, that.info)
                    && java.util.Objects.equals(this.messages, that.messages);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            hash = 31 * hash + java.util.Objects.hashCode(revision);
            hash = 31 * hash + java.util.Objects.hashCode(sequence);
            hash = 31 * hash + java.util.Objects.hashCode(info);
            hash = 31 * hash + java.util.Objects.hashCode(messages);
            return hash;
        }

        @Override public String toString() { return "History[request=" + request + ", group=" + group + ", revision=" + revision + ", sequence=" + sequence + ", info=" + info + ", messages=" + messages + "]"; }

        public History(UUID request, UUID group, long revision, long sequence, GroupInfo info, List<GroupMessage> messages) {
            java.util.Objects.requireNonNull(request);
            if (!info.id().equals(group) || revision < 0 || sequence < 0) throw new IllegalArgumentException("非法群历史身份/版本");
            messages = GroupReplyValidation.bounded(messages);
            if (messages.stream().anyMatch(m -> m.sequence() > sequence)) throw new IllegalArgumentException("群历史越过服务器序号");
            this.request = request;
            this.group = group;
            this.revision = revision;
            this.sequence = sequence;
            this.info = info;
            this.messages = messages;
        }
    }
    final class Message implements GroupReply {
        private final UUID request;
        private final long revision;
        private final GroupSummary summary;

        public UUID request() { return request; }

        public long revision() { return revision; }

        public GroupSummary summary() { return summary; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Message)) return false;
            Message that = (Message) other;
            return java.util.Objects.equals(this.request, that.request)
                    && this.revision == that.revision
                    && java.util.Objects.equals(this.summary, that.summary);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(revision);
            hash = 31 * hash + java.util.Objects.hashCode(summary);
            return hash;
        }

        @Override public String toString() { return "Message[request=" + request + ", revision=" + revision + ", summary=" + summary + "]"; }

        public Message(UUID request, long revision, GroupSummary summary) { java.util.Objects.requireNonNull(request); if (revision < 0 || !summary.last().isPresent()) throw new IllegalArgumentException("推送缺少消息/版本");
            this.request = request;
            this.revision = revision;
            this.summary = summary;
        }
    }
    final class Result implements GroupReply {
        private final UUID request;
        private final UUID group;
        private final GroupStatus status;

        public Result(UUID request, UUID group, GroupStatus status) {
            this.request = request;
            this.group = group;
            this.status = status;
        }

        public UUID request() { return request; }

        public UUID group() { return group; }

        public GroupStatus status() { return status; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Result)) return false;
            Result that = (Result) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group)
                    && java.util.Objects.equals(this.status, that.status);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            hash = 31 * hash + java.util.Objects.hashCode(status);
            return hash;
        }

        @Override public String toString() { return "Result[request=" + request + ", group=" + group + ", status=" + status + "]"; }
}
    final class Details implements GroupReply {
        private final UUID request;
        private final long revision;
        private final GroupInfo info;

        public UUID request() { return request; }

        public long revision() { return revision; }

        public GroupInfo info() { return info; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Details)) return false;
            Details that = (Details) other;
            return java.util.Objects.equals(this.request, that.request)
                    && this.revision == that.revision
                    && java.util.Objects.equals(this.info, that.info);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(revision);
            hash = 31 * hash + java.util.Objects.hashCode(info);
            return hash;
        }

        @Override public String toString() { return "Details[request=" + request + ", revision=" + revision + ", info=" + info + "]"; }

        public Details(UUID request, long revision, GroupInfo info) { java.util.Objects.requireNonNull(request); java.util.Objects.requireNonNull(info); if (revision < 0) throw new IllegalArgumentException("负的成员版本");
            this.request = request;
            this.revision = revision;
            this.info = info;
        }
        public Details(UUID request, GroupInfo info) { this(request, 0, info); }
    }
    final class Updated implements GroupReply {
        private final long revision;
        private final GroupInfo info;

        public long revision() { return revision; }

        public GroupInfo info() { return info; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Updated)) return false;
            Updated that = (Updated) other;
            return this.revision == that.revision
                    && java.util.Objects.equals(this.info, that.info);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(revision);
            hash = 31 * hash + java.util.Objects.hashCode(info);
            return hash;
        }

        @Override public String toString() { return "Updated[revision=" + revision + ", info=" + info + "]"; }

        public Updated(long revision, GroupInfo info) { java.util.Objects.requireNonNull(info); if (revision < 0) throw new IllegalArgumentException("负的成员版本");
            this.revision = revision;
            this.info = info;
        }
    }
    final class Deleted implements GroupReply {
        private final UUID request;
        private final UUID group;
        private final UUID message;
        private final ChatDeletionResult status;
        private final long revision;
        private final List<GroupMessage> messages;

        public UUID request() { return request; }

        public UUID group() { return group; }

        public UUID message() { return message; }

        public ChatDeletionResult status() { return status; }

        public long revision() { return revision; }

        public List<GroupMessage> messages() { return messages; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Deleted)) return false;
            Deleted that = (Deleted) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group)
                    && java.util.Objects.equals(this.message, that.message)
                    && java.util.Objects.equals(this.status, that.status)
                    && this.revision == that.revision
                    && java.util.Objects.equals(this.messages, that.messages);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            hash = 31 * hash + java.util.Objects.hashCode(message);
            hash = 31 * hash + java.util.Objects.hashCode(status);
            hash = 31 * hash + java.util.Objects.hashCode(revision);
            hash = 31 * hash + java.util.Objects.hashCode(messages);
            return hash;
        }

        @Override public String toString() { return "Deleted[request=" + request + ", group=" + group + ", message=" + message + ", status=" + status + ", revision=" + revision + ", messages=" + messages + "]"; }

        public Deleted(UUID request, UUID group, UUID message, ChatDeletionResult status, long revision, List<GroupMessage> messages) {
            java.util.Objects.requireNonNull(request); java.util.Objects.requireNonNull(group); java.util.Objects.requireNonNull(message); java.util.Objects.requireNonNull(status);
            if (revision < 0) throw new IllegalArgumentException("负的删除版本"); messages = GroupReplyValidation.bounded(messages);
            this.request = request;
            this.group = group;
            this.message = message;
            this.status = status;
            this.revision = revision;
            this.messages = messages;
        }
    }
}

final class GroupReplyValidation {
    private GroupReplyValidation() {}

    static List<GroupMessage> bounded(List<GroupMessage> messages) {
        if (messages.size() > GroupLimits.MESSAGES) throw new IllegalArgumentException("群历史超限");
        long previous = 0; HashSet<UUID> ids = new java.util.HashSet<UUID>();
        for (com.november.mcphone.feature.chat.group.GroupMessage m : messages) {
            if (m.sequence() <= previous || !ids.add(m.message().id())) throw new IllegalArgumentException("群历史无序/重复");
            previous = m.sequence();
        }
        return ImmutableList.copyOf(messages);
    }
}
