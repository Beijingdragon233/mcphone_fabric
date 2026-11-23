package com.november.mcphone.feature.chat.group.net;

import com.google.common.collect.ImmutableList;

import com.november.mcphone.feature.chat.ChatImage;
import com.november.mcphone.feature.chat.TextBody;
import com.november.mcphone.feature.chat.group.GroupLimits;
import java.util.List;
import java.util.UUID;

/** 类型明确的群请求；唯一请求票据用于匹配创建、删除和异步上传结果。 */
public interface GroupCommand {
    UUID request();
    default UUID group() { return null; }
    final class ListRooms implements GroupCommand {
        private final UUID request;

        public UUID request() { return request; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ListRooms)) return false;
            ListRooms that = (ListRooms) other;
            return java.util.Objects.equals(this.request, that.request);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            return hash;
        }

        @Override public String toString() { return "ListRooms[request=" + request + "]"; }
 public ListRooms(UUID request) { GroupCommandValidation.required(request);
            this.request = request;
        } }
    final class Notices implements GroupCommand {
        private final UUID request;

        public UUID request() { return request; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Notices)) return false;
            Notices that = (Notices) other;
            return java.util.Objects.equals(this.request, that.request);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            return hash;
        }

        @Override public String toString() { return "Notices[request=" + request + "]"; }
 public Notices(UUID request) { GroupCommandValidation.required(request);
            this.request = request;
        } }
    final class RespondInvitation implements GroupCommand {
        private final UUID request;
        private final UUID group;
        private final UUID invitation;
        private final boolean accept;

        public UUID request() { return request; }

        public UUID group() { return group; }

        public UUID invitation() { return invitation; }

        public boolean accept() { return accept; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RespondInvitation)) return false;
            RespondInvitation that = (RespondInvitation) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group)
                    && java.util.Objects.equals(this.invitation, that.invitation)
                    && this.accept == that.accept;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            hash = 31 * hash + java.util.Objects.hashCode(invitation);
            hash = 31 * hash + java.util.Objects.hashCode(accept);
            return hash;
        }

        @Override public String toString() { return "RespondInvitation[request=" + request + ", group=" + group + ", invitation=" + invitation + ", accept=" + accept + "]"; }

        public RespondInvitation(UUID request, UUID group, UUID invitation, boolean accept) { GroupCommandValidation.required(request, group, invitation);
            this.request = request;
            this.group = group;
            this.invitation = invitation;
            this.accept = accept;
        }
    }
    final class Create implements GroupCommand {
        private final UUID request;
        private final String name;
        private final List<UUID> members;

        public UUID request() { return request; }

        public String name() { return name; }

        public List<UUID> members() { return members; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Create)) return false;
            Create that = (Create) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.name, that.name)
                    && java.util.Objects.equals(this.members, that.members);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(name);
            hash = 31 * hash + java.util.Objects.hashCode(members);
            return hash;
        }

        @Override public String toString() { return "Create[request=" + request + ", name=" + name + ", members=" + members + "]"; }

        public Create(UUID request, String name, List<UUID> members) { GroupCommandValidation.required(request); GroupCommandValidation.bounded(name, GroupLimits.NAME_LENGTH); members = ImmutableList.copyOf(members);
            if (members.isEmpty() || members.size() >= GroupLimits.MEMBERS) throw new IllegalArgumentException("建群人数超限");
            this.request = request;
            this.name = name;
            this.members = members;
        }
    }
    final class History implements GroupCommand {
        private final UUID request;
        private final UUID group;

        public UUID request() { return request; }

        public UUID group() { return group; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof History)) return false;
            History that = (History) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            return hash;
        }

        @Override public String toString() { return "History[request=" + request + ", group=" + group + "]"; }
 public History(UUID request, UUID group) { GroupCommandValidation.required(request, group);
            this.request = request;
            this.group = group;
        } }
    final class SendText implements GroupCommand {
        private final UUID request;
        private final UUID group;
        private final String text;
        private final java.util.Optional<UUID> reply;

        public UUID request() { return request; }

        public UUID group() { return group; }

        public String text() { return text; }

        public java.util.Optional<UUID> reply() { return reply; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SendText)) return false;
            SendText that = (SendText) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group)
                    && java.util.Objects.equals(this.text, that.text)
                    && java.util.Objects.equals(this.reply, that.reply);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            hash = 31 * hash + java.util.Objects.hashCode(text);
            hash = 31 * hash + java.util.Objects.hashCode(reply);
            return hash;
        }

        @Override public String toString() { return "SendText[request=" + request + ", group=" + group + ", text=" + text + ", reply=" + reply + "]"; }

        public SendText(UUID request, UUID group, String text) { this(request,group,text,java.util.Optional.empty()); }
        public SendText(UUID request, UUID group, String text, java.util.Optional<UUID> reply) { java.util.Objects.requireNonNull(reply); GroupCommandValidation.required(request, group); GroupCommandValidation.bounded(text, TextBody.MAX_LENGTH);
            this.request = request;
            this.group = group;
            this.text = text;
            this.reply = reply;
        }
    }
    final class Delete implements GroupCommand {
        private final UUID request;
        private final UUID group;
        private final UUID message;

        public UUID request() { return request; }

        public UUID group() { return group; }

        public UUID message() { return message; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Delete)) return false;
            Delete that = (Delete) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group)
                    && java.util.Objects.equals(this.message, that.message);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            hash = 31 * hash + java.util.Objects.hashCode(message);
            return hash;
        }

        @Override public String toString() { return "Delete[request=" + request + ", group=" + group + ", message=" + message + "]"; }
 public Delete(UUID request, UUID group, UUID message) { GroupCommandValidation.required(request, group, message);
            this.request = request;
            this.group = group;
            this.message = message;
        } }
    final class MarkRead implements GroupCommand {
        private final UUID request;
        private final UUID group;
        private final long sequence;

        public UUID request() { return request; }

        public UUID group() { return group; }

        public long sequence() { return sequence; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MarkRead)) return false;
            MarkRead that = (MarkRead) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group)
                    && this.sequence == that.sequence;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            hash = 31 * hash + java.util.Objects.hashCode(sequence);
            return hash;
        }

        @Override public String toString() { return "MarkRead[request=" + request + ", group=" + group + ", sequence=" + sequence + "]"; }
 public MarkRead(UUID request, UUID group, long sequence) { GroupCommandValidation.required(request, group);
            this.request = request;
            this.group = group;
            this.sequence = sequence;
        } }
    final class Details implements GroupCommand {
        private final UUID request;
        private final UUID group;

        public UUID request() { return request; }

        public UUID group() { return group; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Details)) return false;
            Details that = (Details) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            return hash;
        }

        @Override public String toString() { return "Details[request=" + request + ", group=" + group + "]"; }
 public Details(UUID request, UUID group) { GroupCommandValidation.required(request, group);
            this.request = request;
            this.group = group;
        } }
    final class Invite implements GroupCommand {
        private final UUID request;
        private final UUID group;
        private final List<UUID> members;

        public UUID request() { return request; }

        public UUID group() { return group; }

        public List<UUID> members() { return members; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Invite)) return false;
            Invite that = (Invite) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group)
                    && java.util.Objects.equals(this.members, that.members);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            hash = 31 * hash + java.util.Objects.hashCode(members);
            return hash;
        }

        @Override public String toString() { return "Invite[request=" + request + ", group=" + group + ", members=" + members + "]"; }

        public Invite(UUID request, UUID group, List<UUID> members) { GroupCommandValidation.required(request, group); members = ImmutableList.copyOf(members);
            if (members.isEmpty() || members.size() >= GroupLimits.MEMBERS) throw new IllegalArgumentException("邀请人数超限");
            this.request = request;
            this.group = group;
            this.members = members;
        }
    }
    final class Leave implements GroupCommand {
        private final UUID request;
        private final UUID group;

        public UUID request() { return request; }

        public UUID group() { return group; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Leave)) return false;
            Leave that = (Leave) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            return hash;
        }

        @Override public String toString() { return "Leave[request=" + request + ", group=" + group + "]"; }
 public Leave(UUID request, UUID group) { GroupCommandValidation.required(request, group);
            this.request = request;
            this.group = group;
        } }
    final class Dissolve implements GroupCommand {
        private final UUID request;
        private final UUID group;

        public UUID request() { return request; }

        public UUID group() { return group; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Dissolve)) return false;
            Dissolve that = (Dissolve) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            return hash;
        }

        @Override public String toString() { return "Dissolve[request=" + request + ", group=" + group + "]"; }
 public Dissolve(UUID request, UUID group) { GroupCommandValidation.required(request, group);
            this.request = request;
            this.group = group;
        } }
    final class Rename implements GroupCommand {
        private final UUID request;
        private final UUID group;
        private final String name;

        public UUID request() { return request; }

        public UUID group() { return group; }

        public String name() { return name; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Rename)) return false;
            Rename that = (Rename) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group)
                    && java.util.Objects.equals(this.name, that.name);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            hash = 31 * hash + java.util.Objects.hashCode(name);
            return hash;
        }

        @Override public String toString() { return "Rename[request=" + request + ", group=" + group + ", name=" + name + "]"; }

        public Rename(UUID request, UUID group, String name) { GroupCommandValidation.required(request, group); GroupCommandValidation.bounded(name, GroupLimits.NAME_LENGTH);
            this.request = request;
            this.group = group;
            this.name = name;
        }
    }
    final class Images implements GroupCommand {
        private final UUID request;
        private final UUID group;
        private final List<UUID> images;

        public UUID request() { return request; }

        public UUID group() { return group; }

        public List<UUID> images() { return images; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Images)) return false;
            Images that = (Images) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group)
                    && java.util.Objects.equals(this.images, that.images);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            hash = 31 * hash + java.util.Objects.hashCode(images);
            return hash;
        }

        @Override public String toString() { return "Images[request=" + request + ", group=" + group + ", images=" + images + "]"; }

        public Images(UUID request, UUID group, List<UUID> images) { GroupCommandValidation.required(request, group); images = ImmutableList.copyOf(images); if (images.size() > GroupLimits.IMAGE_REQUEST_IDS) throw new IllegalArgumentException("图片批量超限");
            this.request = request;
            this.group = group;
            this.images = images;
        }
    }
    final class Upload implements GroupCommand {
        private final UUID request;
        private final UUID group;
        private final int width;
        private final int height;
        private final int frames;
        private final int frameMs;
        private final int index;
        private final int count;
        private final byte[] data;

        public UUID request() { return request; }

        public UUID group() { return group; }

        public int width() { return width; }

        public int height() { return height; }

        public int frames() { return frames; }

        public int frameMs() { return frameMs; }

        public int index() { return index; }

        public int count() { return count; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Upload)) return false;
            Upload that = (Upload) other;
            return java.util.Objects.equals(this.request, that.request)
                    && java.util.Objects.equals(this.group, that.group)
                    && this.width == that.width
                    && this.height == that.height
                    && this.frames == that.frames
                    && this.frameMs == that.frameMs
                    && this.index == that.index
                    && this.count == that.count
                    && java.util.Objects.equals(this.data, that.data);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(group);
            hash = 31 * hash + java.util.Objects.hashCode(width);
            hash = 31 * hash + java.util.Objects.hashCode(height);
            hash = 31 * hash + java.util.Objects.hashCode(frames);
            hash = 31 * hash + java.util.Objects.hashCode(frameMs);
            hash = 31 * hash + java.util.Objects.hashCode(index);
            hash = 31 * hash + java.util.Objects.hashCode(count);
            hash = 31 * hash + java.util.Objects.hashCode(data);
            return hash;
        }

        @Override public String toString() { return "Upload[request=" + request + ", group=" + group + ", width=" + width + ", height=" + height + ", frames=" + frames + ", frameMs=" + frameMs + ", index=" + index + ", count=" + count + ", data=" + data + "]"; }

        public Upload(UUID request, UUID group, int width, int height, int frames, int frameMs, int index, int count, byte[] data) { GroupCommandValidation.required(request, group); data = data.clone();
            // 解码上限是固定协议容量；实际配置在服务端接收首片时检查，不能因热改配置踢掉玩家。
            if (data.length > ChatImage.CHUNK_BYTES || count < 1 || count > ChatImage.MAX_BYTES_CEILING / ChatImage.CHUNK_BYTES + 1
                    || index < 0 || index >= count) throw new IllegalArgumentException("图片分片超限");
            this.request = request;
            this.group = group;
            this.width = width;
            this.height = height;
            this.frames = frames;
            this.frameMs = frameMs;
            this.index = index;
            this.count = count;
            this.data = data;
        }
        public byte[] data() { return data.clone(); }
    }
}

final class GroupCommandValidation {
    private GroupCommandValidation() {}

    static void required(UUID... ids) { for (UUID id : ids) java.util.Objects.requireNonNull(id); }
    static void bounded(String value, int max) {
        if (value == null || value.length() > max) throw new IllegalArgumentException("文本超过协议上限");
    }
}
