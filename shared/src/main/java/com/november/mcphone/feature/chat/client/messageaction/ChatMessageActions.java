package com.november.mcphone.feature.chat.client.messageaction;

import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.client.contextmenu.ChatContextMenuView;
import com.november.mcphone.feature.chat.ChatDeletionResult;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** 消息操作模块的唯一公开入口；依赖由页面注入，内部菜单和几何不暴露给其他页面。 */
public final class ChatMessageActions {
    @FunctionalInterface public interface PanelPainter extends ChatContextMenuView.PanelPainter {}
    @FunctionalInterface public interface DeleteSender { void send(UUID peer, UUID message, UUID request); }
    private static final class Pending {
        private final UUID peer;
        private final UUID message;
        private final UUID request;
        private final long deadline;

        private Pending(UUID peer, UUID message, UUID request, long deadline) {
            this.peer = peer;
            this.message = message;
            this.request = request;
            this.deadline = deadline;
        }

        public UUID peer() { return peer; }

        public UUID message() { return message; }

        public UUID request() { return request; }

        public long deadline() { return deadline; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Pending)) return false;
            Pending that = (Pending) other;
            return java.util.Objects.equals(this.peer, that.peer)
                    && java.util.Objects.equals(this.message, that.message)
                    && java.util.Objects.equals(this.request, that.request)
                    && this.deadline == that.deadline;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(peer);
            hash = 31 * hash + java.util.Objects.hashCode(message);
            hash = 31 * hash + java.util.Objects.hashCode(request);
            hash = 31 * hash + java.util.Objects.hashCode(deadline);
            return hash;
        }

        @Override public String toString() { return "Pending[peer=" + peer + ", message=" + message + ", request=" + request + ", deadline=" + deadline + "]"; }
}
    private final ChatContextMenu menu = new ChatContextMenu();
    private final PanelPainter painter;
    private final Consumer<String> copy;
    private final DeleteSender delete;
    private final Consumer<UUID> cancel;
    private final Consumer<String> notice;
    /** 收藏只传消息身份，正文类型及完整内容由会话解析。 */
    @FunctionalInterface public interface FavoriteSaver { void save(UUID peer, UUID message); }
    private final FavoriteSaver favorite;
    private final java.util.function.BiConsumer<UUID,UUID> reply;
    private Pending pending;

    public ChatMessageActions(PanelPainter painter, Consumer<String> copy, DeleteSender delete,
                              Consumer<UUID> cancel, Consumer<String> notice) {
        this(painter, copy, delete, cancel, notice, null);
    }
    public ChatMessageActions(PanelPainter painter, Consumer<String> copy, DeleteSender delete, Consumer<UUID> cancel, Consumer<String> notice, FavoriteSaver favorite) {
        this(painter,copy,delete,cancel,notice,favorite,null);
    }
    public ChatMessageActions(PanelPainter painter, Consumer<String> copy, DeleteSender delete, Consumer<UUID> cancel, Consumer<String> notice, FavoriteSaver favorite, java.util.function.BiConsumer<UUID,UUID> reply) {
        this.reply = reply;
        this.painter = painter; this.copy = copy; this.delete = delete; this.cancel = cancel; this.notice = notice;
        this.favorite = favorite;
    }
    public boolean isOpen() { return menu.isOpen(); }
    public boolean dismiss() { return menu.dismiss(); }
    public void reset() {
        menu.dismiss();
        if (pending != null) cancel.accept(pending.request());
        pending = null;
    }
    public void open(UUID peer, UUID message, String fullText, String selected, double x, double y) {
        menu.open(new ChatMessageTarget(peer, message, fullText, selected), x, y, favorite != null, reply != null);
    }
    public void reconcile(List<ChatMessage> messages) {
        if (menu.isOpen() && messages.stream().noneMatch(m -> m.id().equals(menu.target().messageId()))) menu.dismiss();
    }
    public void tick(long now) {
        if (pending != null && now >= pending.deadline()) {
            cancel.accept(pending.request()); pending = null;
            notice.accept("mcphone.chat.message_action.timeout");
        }
    }
    public void result(UUID request, ChatDeletionResult result) {
        if (pending == null || !pending.request().equals(request)) return;
        pending = null;
        if (result == ChatDeletionResult.OK) return;
        String key;
        switch (result) {
            case NOT_FOUND: key = "not_found"; break;
            case BUSY: key = "busy"; break;
            case STORAGE_ERROR: key = "storage_error"; break;
            default: key = "forbidden";
        }
        notice.accept("mcphone.chat.message_action." + key);
    }
    public boolean mouseClicked(double x, double y, int button, long now) {
        if (!menu.isOpen()) return false;
        if (button == 1) { menu.dismiss(); return false; }
        ChatMessageTarget target = menu.target();
        ChatMessageAction action = button == 0 ? menu.actionAt(x, y) : null;
        menu.dismiss();
        execute(target, action, now);
        return true;
    }
    /** 菜单只交出意图；这里集中执行回调和约束待确认生命周期。 */
    void execute(ChatMessageTarget target, ChatMessageAction action, long now) {
        if (action == ChatMessageAction.REPLY && reply != null) reply.accept(target.peer(),target.messageId());
        if (action == ChatMessageAction.COPY && target.copyable()) copy.accept(target.copiedText());
        if (action == ChatMessageAction.FAVORITE && favorite != null) favorite.save(target.peer(),target.messageId());
        if (action == ChatMessageAction.DELETE && pending == null) {
            UUID request = UUID.randomUUID();
            pending = new Pending(target.peer(), target.messageId(), request, now + 6000);
            delete.send(target.peer(), target.messageId(), request);
        }
    }
    public void render(GuiGraphics g, Font font, int x, int y, int width, int height, int mx, int my) {
        menu.render(g, font, x, y, width, height, mx, my, pending != null, painter);
    }
}
