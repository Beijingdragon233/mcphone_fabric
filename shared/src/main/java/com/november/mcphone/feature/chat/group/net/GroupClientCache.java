package com.november.mcphone.feature.chat.group.net;

import com.google.common.collect.ImmutableList;
import com.november.mcphone.util.Java8Collections;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.ChatDeletionResult;
import com.november.mcphone.feature.chat.group.*;
import java.util.*;
import java.util.function.Consumer;

/** 群会话专用快照。没有客户端类依赖；旧私聊 UUID 和删除票据不能进入这份缓存。 */
public final class GroupClientCache {
    private GroupClientCache() {}
    private static List<GroupSummary> groups = ImmutableList.of();
    private static List<GroupInviteNotice> invitations = ImmutableList.of();
    private static UUID noticeRequest;
    private static GroupStatus noticesError;
    private static boolean noticesLoaded;
    private static List<GroupMessage> entries = ImmutableList.of();
    private static List<ChatMessage> messages = ImmutableList.of();
    private static UUID open, history, detailRequest, detailGroup, deleting, deleteRequest, infoGroup;
    private static GroupInfo info;
    private static GroupStatus historyError, detailsError;
    private static boolean removed;
    private static long deletionFloor;
    private static long infoRevision;
    private static ChatDeletionResult deleteResult;
    private static final Map<UUID, GroupReply.Result> operations = new LinkedHashMap<>();
    private static Consumer<GroupReply.Message> listener = ignored -> {};
    private static Consumer<GroupReply.Result> resultListener = ignored -> {};
    public static void setMessageListener(Consumer<GroupReply.Message> value) { listener = value; }
    public static void setResultListener(Consumer<GroupReply.Result> value) { resultListener = value; }
    public static List<GroupSummary> groups() { return groups; }
    public static List<GroupInviteNotice> invitations() { return invitations; }
    public static boolean noticesLoaded() { return noticesLoaded; }
    public static GroupStatus noticesError() { return noticesError; }
    public static void requestNotices(UUID request) { noticeRequest = request; noticesError = null; }
    public static List<ChatMessage> messages() { return messages; }
    public static List<GroupMessage> entries() { return entries; }
    public static GroupInfo info() { return info; }
    public static boolean removed() { return removed; }
    public static boolean removed(UUID group) { return Objects.equals(open, group) && removed; }
    public static UUID pendingHistory() { return history; }
    public static GroupStatus consumeHistoryError() { com.november.mcphone.feature.chat.group.GroupStatus out = historyError; historyError = null; return out; }
    public static GroupStatus detailsError() { return detailsError; }
    public static GroupSummary summary(UUID id) { return groups.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null); }
    public static String senderName(ChatMessage message) {
        return entries.stream().filter(m -> m.message().id().equals(message.id())).map(GroupMessage::senderName).findFirst().orElse("");
    }
    public static long sequence() { return entries.isEmpty() ? 0 : entries.get(entries.size() - 1).sequence(); }
    public static UUID open(UUID group) { close(); open = group; history = UUID.randomUUID(); return history; }
    /** HUD 在其他界面关闭后恢复绘制，重新绑定当前群；同群连续帧不重拉或清空快照。 */
    public static UUID activate(UUID group) { return Objects.equals(open, group) ? null : open(group); }
    public static void close() {
        open = history = detailRequest = detailGroup = deleting = deleteRequest = infoGroup = null;
        info = null; infoRevision = 0; historyError = detailsError = null; removed = false; deletionFloor = 0; deleteResult = null; setEntries(ImmutableList.of());
    }
    public static void clear() { close(); groups = ImmutableList.of(); invitations = ImmutableList.of(); noticeRequest = null; noticesError = null; noticesLoaded = false; operations.clear(); }
    public static void track(UUID request) {
        if (!operations.containsKey(request) && operations.size() >= GroupClientPolicy.MAX_PENDING_OPERATIONS)
            operations.remove(operations.keySet().iterator().next());
        operations.put(request, null);
    }
    public static void cancel(UUID request) { operations.remove(request); }
    public static GroupReply.Result consume(UUID request) {
        GroupReply.Result result = operations.get(request); if (result != null) operations.remove(request); return result;
    }
    public static void requestDetails(UUID group, UUID request) {
        if (!Objects.equals(infoGroup, group)) infoRevision = 0;
        infoGroup = group;
        detailGroup = group; detailRequest = request; info = null; detailsError = null;
    }
    public static void cancelDetails(UUID group) {
        if (Objects.equals(detailGroup, group)) { detailGroup = detailRequest = infoGroup = null; info = null; infoRevision = 0; detailsError = null; }
    }
    public static void beginDeletion(UUID group, UUID message, UUID request) {
        if (!Objects.equals(open, group)) return;
        deleting = message; deleteRequest = request; deleteResult = null;
    }
    public static void cancelDeletion(UUID request) {
        if (Objects.equals(deleteRequest, request)) { deleting = deleteRequest = null; deleteResult = null; }
    }
    public static ChatDeletionResult consumeDeleteResult() {
        com.november.mcphone.feature.chat.ChatDeletionResult result = deleteResult; if (result != null) { deleting = deleteRequest = null; deleteResult = null; } return result;
    }
    public static UUID deletionRequest() { return deleteRequest; }
    public static void accept(GroupReply reply) {
        if (reply instanceof GroupReply.Notices) {
            invitations = ((GroupReply.Notices) reply).invitations(); noticesLoaded = true; noticeRequest = null; noticesError = null;
        } else if (reply instanceof GroupReply.Directory) {
            boolean known = summary(open) != null;
            groups = ImmutableList.copyOf(((GroupReply.Directory) reply).groups());
            if (open != null && known && summary(open) == null) { removed = true; info = null; setEntries(ImmutableList.of()); }
        } else if (reply instanceof GroupReply.History) {
            if (!Objects.equals(open, ((GroupReply.History) reply).group()) || !Objects.equals(history, ((GroupReply.History) reply).request()) || ((GroupReply.History) reply).revision() < deletionFloor) return;
            history = null; historyError = null;
            if (detailGroup == null || Objects.equals(detailGroup, ((GroupReply.History) reply).group())) updateInfo(((GroupReply.History) reply).revision(), ((GroupReply.History) reply).info());
            setEntries(merge(((GroupReply.History) reply).messages(), ((GroupReply.History) reply).sequence()));
        } else if (reply instanceof GroupReply.Message) {
            GroupReply.Message v = (GroupReply.Message) reply;
            if (operations.containsKey(v.request()))
                operations.put(v.request(),new GroupReply.Result(v.request(),v.summary().id(),GroupStatus.OK));
            ArrayList<com.november.mcphone.feature.chat.group.GroupSummary> next = new ArrayList<>(groups); next.removeIf(g -> g.id().equals(v.summary().id())); next.add(v.summary());
            next.sort(Comparator.comparingLong(GroupSummary::lastTime).reversed().thenComparing(GroupSummary::id));
            groups = ImmutableList.copyOf(next.subList(0, Math.min(next.size(), GroupLimits.GROUPS_PER_PLAYER)));
            if (Objects.equals(open, v.summary().id()) && !removed && v.revision() >= deletionFloor) {
                com.november.mcphone.feature.chat.group.GroupMessage entry = v.summary().last().orElseThrow(java.util.NoSuchElementException::new); ArrayList<com.november.mcphone.feature.chat.group.GroupMessage> all = new ArrayList<>(entries);
                if (all.stream().noneMatch(m -> m.message().id().equals(entry.message().id()))) { all.add(entry); setEntries(all); }
            }
            listener.accept(v);
        } else if (reply instanceof GroupReply.Details) {
            if (Objects.equals(detailRequest, ((GroupReply.Details) reply).request()) && Objects.equals(detailGroup, ((GroupReply.Details) reply).info().id())) { updateInfo(((GroupReply.Details) reply).revision(), ((GroupReply.Details) reply).info()); detailRequest = null; detailsError = null; }
        } else if (reply instanceof GroupReply.Updated) {
            UUID target = detailGroup == null ? open : detailGroup;
            if (Objects.equals(target, ((GroupReply.Updated) reply).info().id()) && !removed(((GroupReply.Updated) reply).info().id())) updateInfo(((GroupReply.Updated) reply).revision(), ((GroupReply.Updated) reply).info());
        } else if (reply instanceof GroupReply.Deleted) {
            GroupReply.Deleted v = (GroupReply.Deleted) reply;
            if (!Objects.equals(open, v.group()) || !Objects.equals(deleting, v.message()) || !Objects.equals(deleteRequest, v.request())) return;
            deleteResult = v.status();
            if (v.status() == ChatDeletionResult.OK) {
                deletionFloor = Math.max(deletionFloor, v.revision());
                long last = v.messages().stream().mapToLong(GroupMessage::sequence).max().orElse(0);
                List<com.november.mcphone.feature.chat.group.GroupMessage> merged = merge(v.messages(), last).stream().filter(m -> !m.message().id().equals(v.message())).collect(Java8Collections.toList());
                setEntries(merged);
            }
        } else if (reply instanceof GroupReply.Result) {
            if (Objects.equals(noticeRequest, ((GroupReply.Result) reply).request())) { noticesError = ((GroupReply.Result) reply).status(); noticeRequest = null; }
            if (Objects.equals(history, ((GroupReply.Result) reply).request()) && Objects.equals(open, ((GroupReply.Result) reply).group())) historyError = ((GroupReply.Result) reply).status();
            if (Objects.equals(detailRequest, ((GroupReply.Result) reply).request()) && Objects.equals(detailGroup, ((GroupReply.Result) reply).group())) detailsError = ((GroupReply.Result) reply).status();
            if (operations.containsKey(((GroupReply.Result) reply).request())) operations.put(((GroupReply.Result) reply).request(), ((GroupReply.Result) reply));
            if (((GroupReply.Result) reply).status() == GroupStatus.NOT_MEMBER && Objects.equals(open, ((GroupReply.Result) reply).group()) && Objects.equals(history, ((GroupReply.Result) reply).request())) removed = true;
            resultListener.accept(((GroupReply.Result) reply));
        }
    }
    private static void updateInfo(long revision, GroupInfo next) {
        if (!Objects.equals(infoGroup, next.id())) { infoGroup = next.id(); infoRevision = 0; }
        if (revision >= infoRevision) { info = next; infoRevision = revision; }
    }
    private static List<GroupMessage> merge(List<GroupMessage> snapshot, long through) {
        LinkedHashMap<UUID,com.november.mcphone.feature.chat.group.GroupMessage> byId = new LinkedHashMap<UUID, GroupMessage>(); snapshot.forEach(m -> byId.put(m.message().id(), m));
        entries.stream().filter(m -> m.sequence() > through).forEach(m -> byId.put(m.message().id(), m));
        return ImmutableList.copyOf(byId.values());
    }
    private static void setEntries(List<GroupMessage> value) {
        List<com.november.mcphone.feature.chat.group.GroupMessage> sorted = value.stream().sorted(Comparator.comparingLong(GroupMessage::sequence)).collect(Java8Collections.toList());
        entries = ImmutableList.copyOf(sorted.subList(Math.max(0, sorted.size() - GroupLimits.MESSAGES), sorted.size()));
        messages = entries.stream().map(GroupMessage::message).collect(Java8Collections.toList());
    }
}
