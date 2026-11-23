package com.november.mcphone.feature.chat.group.net;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.november.mcphone.util.Java8Collections;

import com.november.mcphone.feature.chat.*;
import com.november.mcphone.feature.chat.group.*;
import com.november.mcphone.feature.chat.net.ChatImageDataPacket;
import com.november.mcphone.core.ServerConfig;
import net.minecraft.Util;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/** 平台只注册两类包；线程切换由网络门面保证，群业务与推送协调集中维护。 */
public final class GroupServer {
    private GroupServer() {}
    private static final GroupThrottle THROTTLE = new GroupThrottle();
    private static final GroupImageAssembly UPLOADS = new GroupImageAssembly();
    private static BiConsumer<ServerPlayer, GroupReply> reply;
    private static BiConsumer<ServerPlayer, ChatImageDataPacket> pixels;
    public static void install(BiConsumer<ServerPlayer, GroupReply> send, BiConsumer<ServerPlayer, ChatImageDataPacket> images) {
        reply = send; pixels = images;
    }
    public static void handle(GroupCommand command, ServerPlayer player) {
        if (reply == null || pixels == null) throw new IllegalStateException("群网络推送未注册");
        try {
            com.november.mcphone.feature.chat.group.GroupData data = GroupData.get(player.level().getServer());
            if (!data.isAvailable()) throw new GroupFault(GroupStatus.UNAVAILABLE);
            GroupImageFiles.sweepOnce(player.level().getServer());
            if (command instanceof GroupCommand.ListRooms) { throttle(player, GroupThrottle.Kind.DIRECTORY); directory(player); }
            else if (command instanceof GroupCommand.Notices) { throttle(player, GroupThrottle.Kind.NOTICES); notices(player); }
            else if (command instanceof GroupCommand.Create) {
                throttle(player, GroupThrottle.Kind.CREATE); com.november.mcphone.feature.chat.group.GroupRoom room = GroupService.create(player, ((GroupCommand.Create) command).name(), ((GroupCommand.Create) command).members());
                refreshMembers(player, room); refreshInvitationRecipients(player, data.book().pendingRecipients(room.id()));
                result(player, ((GroupCommand.Create) command), GroupStatus.OK, room.id());
            } else if (command instanceof GroupCommand.History) {
                throttle(player, GroupThrottle.Kind.HISTORY); com.november.mcphone.feature.chat.group.GroupRoom room = GroupService.read(player, ((GroupCommand.History) command).group());
                reply.accept(player, new GroupReply.History(((GroupCommand.History) command).request(), room.id(), room.revision(), room.sequence(), GroupInfo.of(room), room.visible(player.getUUID())));
            } else if (command instanceof GroupCommand.SendText) {
                throttle(player, GroupThrottle.Kind.TEXT);
                long before = GroupService.read(player,((GroupCommand.SendText) command).group()).sequence();
                com.november.mcphone.feature.chat.group.GroupMessage sent = GroupService.sendText(player,((GroupCommand.SendText) command).group(),((GroupCommand.SendText) command).text(),((GroupCommand.SendText) command).reply(),((GroupCommand.SendText) command).request());
                if (sent.sequence() <= before) result(player,((GroupCommand.SendText) command),GroupStatus.OK,((GroupCommand.SendText) command).group());
                else deliver(player,((GroupCommand.SendText) command).group(),((GroupCommand.SendText) command).request());
            } else if (command instanceof GroupCommand.MarkRead) {
                throttle(player, GroupThrottle.Kind.READ); GroupService.markRead(player, ((GroupCommand.MarkRead) command).group(), ((GroupCommand.MarkRead) command).sequence()); directory(player);
            } else if (command instanceof GroupCommand.Details) {
                throttle(player, GroupThrottle.Kind.DETAILS); com.november.mcphone.feature.chat.group.GroupRoom room = GroupService.read(player, ((GroupCommand.Details) command).group());
                reply.accept(player, new GroupReply.Details(((GroupCommand.Details) command).request(), room.revision(), GroupInfo.of(room)));
            } else if (command instanceof GroupCommand.Delete) delete(((GroupCommand.Delete) command), player);
            else if (command instanceof GroupCommand.Leave) {
                throttle(player, GroupThrottle.Kind.MANAGE); List<UUID> recipients = data.book().pendingRecipients(((GroupCommand.Leave) command).group());
                com.november.mcphone.feature.chat.group.GroupRoom room = GroupService.leave(player, ((GroupCommand.Leave) command).group()); refreshInvitationRecipients(player, recipients);
                refreshMembers(player, room); result(player, ((GroupCommand.Leave) command), GroupStatus.OK, ((GroupCommand.Leave) command).group());
            } else if (command instanceof GroupCommand.Dissolve) {
                throttle(player, GroupThrottle.Kind.MANAGE); List<UUID> recipients = data.book().pendingRecipients(((GroupCommand.Dissolve) command).group());
                com.november.mcphone.feature.chat.group.GroupRoom room = GroupService.dissolve(player, ((GroupCommand.Dissolve) command).group()); refreshInvitationRecipients(player, recipients);
                refreshMembers(player, room); result(player, ((GroupCommand.Dissolve) command), GroupStatus.OK, ((GroupCommand.Dissolve) command).group());
            } else if (command instanceof GroupCommand.Rename) {
                throttle(player, GroupThrottle.Kind.MANAGE); com.november.mcphone.feature.chat.group.GroupRoom room = GroupService.rename(player, ((GroupCommand.Rename) command).group(), ((GroupCommand.Rename) command).name());
                refreshMembers(player, room); refreshInvitationRecipients(player, data.book().pendingRecipients(((GroupCommand.Rename) command).group()));
                result(player, ((GroupCommand.Rename) command), GroupStatus.OK, ((GroupCommand.Rename) command).group());
            } else if (command instanceof GroupCommand.Invite) {
                throttle(player, GroupThrottle.Kind.MANAGE); com.november.mcphone.feature.chat.group.GroupRoom room = GroupService.invite(player, ((GroupCommand.Invite) command).group(), ((GroupCommand.Invite) command).members());
                refreshInvitationRecipients(player, data.book().pendingRecipients(room.id())); result(player, ((GroupCommand.Invite) command), GroupStatus.OK, ((GroupCommand.Invite) command).group());
            } else if (command instanceof GroupCommand.RespondInvitation) {
                throttle(player, GroupThrottle.Kind.MANAGE); com.november.mcphone.feature.chat.group.GroupRoom room = GroupService.respondInvitation(player, ((GroupCommand.RespondInvitation) command).group(), ((GroupCommand.RespondInvitation) command).invitation(), ((GroupCommand.RespondInvitation) command).accept());
                if (((GroupCommand.RespondInvitation) command).accept()) refreshMembers(player, room);
                notices(player); result(player, ((GroupCommand.RespondInvitation) command), GroupStatus.OK, ((GroupCommand.RespondInvitation) command).group());
            } else if (command instanceof GroupCommand.Images) images(((GroupCommand.Images) command), player);
            else if (command instanceof GroupCommand.Upload) upload(((GroupCommand.Upload) command), player);
        } catch (GroupFault error) {
            if (command instanceof GroupCommand.Delete) {
                ChatDeletionResult status = error.status() == GroupStatus.BUSY ? ChatDeletionResult.BUSY
                        : error.status() == GroupStatus.UNAVAILABLE ? ChatDeletionResult.STORAGE_ERROR : ChatDeletionResult.FORBIDDEN;
                reply.accept(player, new GroupReply.Deleted(((GroupCommand.Delete) command).request(), ((GroupCommand.Delete) command).group(), ((GroupCommand.Delete) command).message(), status, 0, ImmutableList.of()));
            } else {
                result(player, command, error.status(), command.group());
                if (command instanceof GroupCommand.RespondInvitation && error.status() == GroupStatus.INVITATION_GONE) notices(player);
            }
        }
    }
    private static void delete(GroupCommand.Delete v, ServerPlayer player) {
        throttle(player, GroupThrottle.Kind.DELETE);
        boolean changed = GroupService.delete(player, v.group(), v.message()); com.november.mcphone.feature.chat.group.GroupRoom room = GroupService.read(player, v.group());
        reply.accept(player, new GroupReply.Deleted(v.request(), v.group(), v.message(), changed ? ChatDeletionResult.OK
                : ChatDeletionResult.NOT_FOUND, room.revision(), room.visible(player.getUUID())));
        directory(player);
    }
    private static void upload(GroupCommand.Upload v, ServerPlayer player) {
        GroupService.requirePhone(player); GroupService.read(player, v.group());
        if (!ServerConfig.allowChatImages()) throw new GroupFault(GroupStatus.INVALID);
        if (v.index() == 0) {
            throttle(player, GroupThrottle.Kind.UPLOAD);
            if (v.count() > ChatImage.maxChunks()) throw new GroupFault(GroupStatus.INVALID);
        }
        com.november.mcphone.feature.chat.group.GroupImageAssembly.Complete done = UPLOADS.accept(player, v, ChatImage.maxBytes(), System.currentTimeMillis()); if (done == null) return;
        if (!ChatImageStore.looksLikePng(done.png())) throw new GroupFault(GroupStatus.INVALID);
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        Util.backgroundExecutor().execute(() -> {
            UUID image = GroupImageFiles.write(server, v.group(), done.png());
            server.execute(() -> {
                if (image == null) { result(player, v, GroupStatus.UNAVAILABLE, v.group()); return; }
                try { GroupService.sendImage(player, v.group(), image, v.width(), v.height(), v.frames(), v.frameMs()); deliver(player, v.group(), v.request()); }
                catch (GroupFault error) { result(player, v, error.status(), v.group()); }
                finally {
                    GroupImageFiles.release(server, image);
                    com.november.mcphone.feature.chat.group.GroupData data = GroupData.get(server);
                    if (data.isAvailable()) GroupImageFiles.clean(server, ImmutableSet.of(image), GroupService.imageReferences(server, v.group()));
                }
            });
        });
    }
    private static void images(GroupCommand.Images v, ServerPlayer player) {
        throttle(player, GroupThrottle.Kind.IMAGES);
        List<UUID> allowed = v.images().stream().distinct().filter(id -> GroupService.mayReadImage(player, v.group(), id)).collect(Java8Collections.toList());
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        Util.backgroundExecutor().execute(() -> {
            int total = 0;
            for (UUID id : allowed) {
                byte[] data = GroupImageFiles.read(server, id);
                if (data != null && total + data.length > ChatImage.maxBytes()) break;
                if (data != null) total += data.length;
                com.november.mcphone.feature.chat.net.ChatImageDataPacket packet = data == null ? ChatImageDataPacket.gone(id) : new ChatImageDataPacket(id, data);
                server.execute(() -> { if (!player.hasDisconnected() && GroupService.mayReadImage(player, v.group(), id)) pixels.accept(player, packet); });
            }
        });
    }
    private static void throttle(ServerPlayer player, GroupThrottle.Kind kind) {
        if (!THROTTLE.allow(player, kind, System.currentTimeMillis())) throw new GroupFault(GroupStatus.BUSY);
    }
    private static void directory(ServerPlayer player) { if (!player.hasDisconnected()) reply.accept(player, new GroupReply.Directory(GroupService.summaries(player))); }
    private static void notices(ServerPlayer player) { if (!player.hasDisconnected()) reply.accept(player, new GroupReply.Notices(GroupService.notices(player))); }
    private static void refreshInvitationRecipients(ServerPlayer actor, List<UUID> recipients) {
        net.minecraft.server.MinecraftServer server = actor.level().getServer();
        for (UUID id : recipients) { net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(id); if (player != null) notices(player); }
    }
    private static void result(ServerPlayer player, GroupCommand command, GroupStatus status, UUID group) {
        if (!player.hasDisconnected()) reply.accept(player, new GroupReply.Result(command.request(), group, status));
    }
    private static void refreshMembers(ServerPlayer actor, GroupRoom room) {
        net.minecraft.server.MinecraftServer server = actor.level().getServer();
        for (com.november.mcphone.feature.chat.group.GroupMember member : room.members()) {
            net.minecraft.server.level.ServerPlayer online = server.getPlayerList().getPlayer(member.id()); if (online == null) continue;
            directory(online);
            // 离群/解散也刷新旧成员目录，但资料推送必须重新读取并验证当前资格。
            try { com.november.mcphone.feature.chat.group.GroupRoom current = GroupService.read(online, room.id()); reply.accept(online, new GroupReply.Updated(current.revision(), GroupInfo.of(current))); }
            catch (GroupFault gone) { if (gone.status() != GroupStatus.NOT_MEMBER) throw gone; }
        }
    }
    private static void deliver(ServerPlayer actor, UUID group, UUID request) {
        com.november.mcphone.feature.chat.group.GroupRoom room = GroupService.read(actor, group); net.minecraft.server.MinecraftServer server = actor.level().getServer();
        for (com.november.mcphone.feature.chat.group.GroupMember member : room.members()) {
            net.minecraft.server.level.ServerPlayer online = server.getPlayerList().getPlayer(member.id());
            if (online != null) reply.accept(online, new GroupReply.Message(request, room.revision(), GroupSummary.of(room, member.id())));
        }
    }
}
