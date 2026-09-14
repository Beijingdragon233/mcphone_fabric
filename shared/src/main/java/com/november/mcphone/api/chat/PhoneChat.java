package com.november.mcphone.api.chat;

import com.november.mcphone.core.PhonePlayerData;
import com.november.mcphone.feature.chat.ChatData;
import com.november.mcphone.feature.chat.ChatDelivery;
import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.ChatService;
import com.november.mcphone.feature.chat.FriendData;
import com.november.mcphone.feature.chat.FriendGuard;
import com.november.mcphone.feature.chat.TextBody;
import com.november.mcphone.util.TextSanitizer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * 聊天的服务端入口。线程、权限与兼容的约定见 {@link com.november.mcphone.api.chat 包说明}。
 */
public final class PhoneChat {

    private PhoneChat() {}

    /** 一条私信的字数上限（按 char 数）。发超了 {@link #sendText} 返回 TEXT_TOO_LONG，不会替你截断 */
    public static int maxTextLength() {
        return TextBody.MAX_LENGTH;
    }

    /** 服务端见过的玩家：在线，或 MCphone 的名字缓存、原版资料缓存里有记录。不会去问 Mojang */
    public static Optional<PhoneContact> findPlayer(MinecraftServer server, UUID id) {
        Objects.requireNonNull(id, "id");
        requireServerThread(server);

        FriendData friends = FriendData.get(server);
        if (!ChatService.isKnownPlayer(server, friends, id)) return Optional.empty();
        return Optional.of(contact(server, friends, id));
    }

    /**
     * 按名字找，不分大小写。先找在线的；不在线就查 MCphone 的名字缓存，缓存里对上不止一个人
     * （有人改过名）时返回空，不猜。原版资料缓存不查：它按名字查不到时会去问 Mojang，卡住主线程。
     */
    public static Optional<PhoneContact> findPlayer(MinecraftServer server, String name) {
        Objects.requireNonNull(name, "name");
        requireServerThread(server);

        FriendData friends = FriendData.get(server);
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return Optional.of(contact(server, friends, online.getUUID()));

        List<UUID> cached = friends.idsNamed(name);
        if (cached.size() != 1) return Optional.empty();
        return Optional.of(contact(server, friends, cached.get(0)));
    }

    /** 这个玩家的好友，按名字排序。玩家不在线也能查 */
    public static List<PhoneContact> contacts(MinecraftServer server, UUID player) {
        Objects.requireNonNull(player, "player");
        requireServerThread(server);

        FriendData friends = FriendData.get(server);
        List<PhoneContact> out = new ArrayList<>();
        for (UUID peer : friends.getFriends(player)) {
            out.add(contact(server, friends, peer));
        }
        out.sort(Comparator.comparing(PhoneContact::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PhoneContact::id));
        return List.copyOf(out);
    }

    /** self 看 other 是什么关系 */
    public static ContactRelation relation(MinecraftServer server, UUID self, UUID other) {
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(other, "other");
        requireServerThread(server);

        if (self.equals(other)) return ContactRelation.SELF;
        FriendData friends = FriendData.get(server);
        if (friends.areFriends(self, other)) return ContactRelation.FRIEND;
        if (friends.hasRequest(self, other)) return ContactRelation.REQUEST_SENT;
        if (friends.hasRequest(other, self)) return ContactRelation.REQUEST_RECEIVED;
        return ContactRelation.NONE;
    }

    /**
     * 两人之间的私信会话。会话随好友关系存在，不需要也不能单独创建：是好友就有，不是就返回空。
     * 未读数是 self 的已读进度算出来的，所以 self 得是在线的 ServerPlayer。
     */
    public static Optional<PhoneConversation> conversation(ServerPlayer self, UUID peer) {
        Objects.requireNonNull(peer, "peer");
        MinecraftServer server = self.server;
        requireServerThread(server);

        UUID selfId = self.getUUID();
        FriendData friends = FriendData.get(server);
        if (!friends.areFriends(selfId, peer)) return Optional.empty();

        long since = PhonePlayerData.of(self).chatRead().getLastRead(peer);
        ChatData.Tail tail = ChatData.get(server).tail(selfId, peer, since);
        OptionalLong last = tail.last() == null ? OptionalLong.empty() : OptionalLong.of(tail.last().time());
        return Optional.of(new PhoneConversation(selfId, contact(server, friends, peer), tail.unread(), last));
    }

    /**
     * 以 sender 的名义给 recipient 发一条文本私信，和他在手机上自己发的一样：存进聊天记录，
     * 双方在线就立刻出现在界面上并弹通知。
     *
     * <p>§ 格式符与控制字符会被去掉、首尾空白会被裁掉，剩下的超过 {@link #maxTextLength()} 就整条拒收。
     * 每对会话只留最近 100 条，发得多会把两人真正的聊天挤掉。
     */
    public static SendResult sendText(ServerPlayer sender, UUID recipient, String text) {
        Objects.requireNonNull(recipient, "recipient");
        MinecraftServer server = sender.server;
        requireServerThread(server);

        UUID senderId = sender.getUUID();
        if (senderId.equals(recipient)) return SendResult.SELF;
        if (!FriendData.get(server).areFriends(senderId, recipient)) return SendResult.NOT_FRIENDS;
        if (!FriendGuard.carriesPhone(sender)) return SendResult.NO_PHONE;

        String clean = TextSanitizer.sanitize(text, Integer.MAX_VALUE);
        if (clean.isEmpty()) return SendResult.EMPTY_TEXT;
        if (clean.length() > TextBody.MAX_LENGTH) return SendResult.TEXT_TOO_LONG;

        ChatDelivery.requireInstalled();
        ChatMessage message = ChatService.storeText(sender, recipient, clean);
        return ChatDelivery.deliver(sender, recipient, message)
                ? SendResult.DELIVERED
                : SendResult.STORED_OFFLINE;
    }

    private static PhoneContact contact(MinecraftServer server, FriendData friends, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        return new PhoneContact(id, ChatService.resolveName(server, friends, id, online), online != null);
    }

    private static void requireServerThread(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new IllegalStateException("[MCphone] PhoneChat 只能在服务端主线程调用，当前线程: "
                    + Thread.currentThread().getName());
        }
    }
}
