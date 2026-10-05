package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.PhoneItem;
import com.november.mcphone.core.client.PhoneScreen;
import com.november.mcphone.core.client.PhoneToast;
import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.net.ChatClientCache;
import com.november.mcphone.feature.chat.net.ConversationSummary;
import com.november.mcphone.platform.client.Screens;
import com.november.mcphone.platform.client.Toasts;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/**
 * 收到消息时在游戏里弹通知。由 ChatClientCache 的回调装上：网络层在专用服务器上
 * 也会加载，不能直接引用这里的客户端类。
 * 三种不提醒：自己发的回声、正看着这个会话、手机不在身上（检查在客户端，背包就在手上）。
 */
public final class ChatNotifier {

    private ChatNotifier() {}

    /** 收到一条消息（自己发的回声也会走到这里） */
    public static void onMessage(UUID peer, ChatMessage message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 自己刚发的那张图，回声一到就把像素直接塞进缓存，省得再从服务器下回来一遍。
        // 借道这里是因为这儿本来就是"每条新消息都会经过"的地方
        ChatImageSender.onNewMessage(message);

        if (message.sender().equals(mc.player.getUUID())) return;
        if (isViewing(peer)) return;
        if (!PhoneItem.isCarriedBy(mc.player)) return;

        show(mc, peer, message);
    }

    private static boolean isViewing(UUID peer) {
        return Screens.current(Minecraft.getInstance()) instanceof PhoneScreen phone
                && phone.isViewingConversation(peer);
    }

    /** 同一个人已有一条就合并进去，否则连发几条会占满原版通知区的 5 个槽位 */
    private static void show(Minecraft mc, UUID peer, ChatMessage message) {
        // 类型名两支不同（26.x 那个管理器改了名，也不再挂在 Minecraft 上），
        // 取法两支也不同，所以这一层挡住类型、调用方拿 var 接
        var toasts = Toasts.manager(mc);

        PhoneToast existing = toasts.getToast(PhoneToast.class, peer);
        if (existing != null) {
            existing.addMessage(PhoneToast.preview(message));
            return;
        }

        toasts.addToast(new PhoneToast(peer, resolveName(peer), PhoneToast.preview(message)));
    }

    private static String resolveName(UUID peer) {
        for (ConversationSummary c : ChatClientCache.getConversations()) {
            if (c.id().equals(peer)) return c.name();
        }
        return PhoneToast.fallbackName(peer);
    }
}
