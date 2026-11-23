package com.november.mcphone.feature.chat.client;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.PlayerAvatar;
import com.november.mcphone.feature.chat.group.*;
import com.november.mcphone.feature.chat.group.net.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 群邀请收件箱：按钮按本帧邀请 ID 命中，同意/拒绝必须等服务器确认；不自行授予成员资格。 */
final class ChatGroupNotices {
    static final int ROW_HEIGHT = 34;
    static final class Row {
        private final GroupInviteNotice invitation;
        private final ChatLayout.Rect bounds;
        private final ChatLayout.Rect accept;
        private final ChatLayout.Rect decline;

        Row(GroupInviteNotice invitation, ChatLayout.Rect bounds, ChatLayout.Rect accept, ChatLayout.Rect decline) {
            this.invitation = invitation;
            this.bounds = bounds;
            this.accept = accept;
            this.decline = decline;
        }

        public GroupInviteNotice invitation() { return invitation; }

        public ChatLayout.Rect bounds() { return bounds; }

        public ChatLayout.Rect accept() { return accept; }

        public ChatLayout.Rect decline() { return decline; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Row)) return false;
            Row that = (Row) other;
            return java.util.Objects.equals(this.invitation, that.invitation)
                    && java.util.Objects.equals(this.bounds, that.bounds)
                    && java.util.Objects.equals(this.accept, that.accept)
                    && java.util.Objects.equals(this.decline, that.decline);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(invitation);
            hash = 31 * hash + java.util.Objects.hashCode(bounds);
            hash = 31 * hash + java.util.Objects.hashCode(accept);
            hash = 31 * hash + java.util.Objects.hashCode(decline);
            return hash;
        }

        @Override public String toString() { return "Row[invitation=" + invitation + ", bounds=" + bounds + ", accept=" + accept + ", decline=" + decline + "]"; }
}
    private final Consumer<GroupCommand> send;
    private ChatLayout.Rect area;
    private List<Row> hits = ImmutableList.of();
    private int scroll;
    private UUID request, group;
    private boolean accepting;
    private long started;
    private String status = "";
    ChatGroupNotices() { this(GroupTransport::send); }
    ChatGroupNotices(Consumer<GroupCommand> send) { this.send = send; }
    void open() { close(); }
    void close() {
        if (request != null) GroupClientCache.cancel(request);
        request = group = null; area = null; hits = ImmutableList.of(); scroll = 0; status = "";
    }
    static List<Row> layout(List<GroupInviteNotice> invitations, ChatLayout.Rect area, int offset) {
        int visible = Math.max(0, area.height() / ROW_HEIGHT);
        int first = Math.max(0, Math.min(offset, Math.max(0, invitations.size() - visible)));
        int buttonWidth = Math.min(23, Math.max(8, (area.width() - 26) / 2));
        ArrayList<ChatGroupNotices.Row> rows = new ArrayList<Row>();
        for (int i = first; i < invitations.size() && i < first + visible; i++) {
            int y = area.y() + (i - first) * ROW_HEIGHT;
            ChatLayout.Rect bounds = new ChatLayout.Rect(area.x(), y, area.width(), ROW_HEIGHT - 2);
            rows.add(new Row(invitations.get(i), bounds, new ChatLayout.Rect(area.x() + area.width() - buttonWidth * 2 - 5, y + 17, buttonWidth, 12),
                    new ChatLayout.Rect(area.x() + area.width() - buttonWidth - 3, y + 17, buttonWidth, 12)));
        }
        return ImmutableList.copyOf(rows);
    }
    private void pollResult() {
        if (request == null) return;
        com.november.mcphone.feature.chat.group.net.GroupReply.Result result = GroupClientCache.consume(request);
        if (result != null && group.equals(result.group())) {
            status = result.status() == GroupStatus.OK ? accepting ? "mcphone.chat.group.invite_accepted" : "mcphone.chat.group.invite_declined" : result.status().key();
            request = group = null;
        } else if (System.currentTimeMillis() - started > GroupClientPolicy.OPERATION_TIMEOUT_MS) {
            GroupClientCache.cancel(request); request = group = null; status = "mcphone.chat.group.timeout";
        }
    }
    void render(GuiGraphics g, Font font, ChatLayout.Rect bounds, int mx, int my) {
        pollResult(); area = new ChatLayout.Rect(bounds.x(), bounds.y(), bounds.width(), Math.max(0, bounds.height() - 20));
        List<com.november.mcphone.feature.chat.group.GroupInviteNotice> invitations = GroupClientCache.invitations();
        int visible = area.height() / ROW_HEIGHT; scroll = Math.max(0, Math.min(scroll, Math.max(0, invitations.size() - visible)));
        hits = layout(invitations, area, scroll);
        com.november.mcphone.feature.chat.group.GroupStatus error = GroupClientCache.noticesError();
        if (invitations.isEmpty()) {
            String key = error != null ? error.key() : GroupClientCache.noticesLoaded() ? "mcphone.chat.contacts.group_notices_empty" : "mcphone.chat.group.loading";
            ChatUi.text(g, font, ChatUi.truncate(font, Component.translatable(key).getString(), area.width() - 6, ChatLayout.TEXT_SCALE),
                    area.x() + 3, area.y() + 3, ChatLayout.TEXT_SCALE, FontPalette.subtle());
        }
        for (ChatGroupNotices.Row row : hits) {
            ChatLayout.Rect b = row.bounds(); com.november.mcphone.feature.chat.group.GroupInviteNotice invitation = row.invitation();
            ChatUi.glass(g, ChatGlass.Surface.CARD, b.x(), b.y(), b.width(), b.height(), false);
            ChatUi.text(g, font, ChatUi.truncate(font, invitation.name(), b.width() - 6, ChatLayout.TEXT_SCALE),
                    b.x() + 3, b.y() + 3, ChatLayout.TEXT_SCALE, FontPalette.title());
            PlayerAvatar.draw(g, invitation.inviter().id(), b.x() + 3, b.y() + 16, 12);
            ChatUi.text(g, font, ChatUi.truncate(font, invitation.inviter().name(), Math.max(0, row.accept().x() - b.x() - 21), ChatLayout.META_SCALE),
                    b.x() + 18, b.y() + 19, ChatLayout.META_SCALE, FontPalette.subtle());
            button(g, font, row.accept(), "mcphone.chat.group.invite_accept", mx, my);
            button(g, font, row.decline(), "mcphone.chat.group.invite_decline", mx, my);
        }
        String hint = request != null ? "mcphone.chat.group.pending" : !status.isEmpty() ? status : error != null ? error.key() : "mcphone.chat.group.invite_notice_hint";
        int y = bounds.y() + bounds.height() - 17;
        for (net.minecraft.util.FormattedCharSequence line : font.split(Component.translatable(hint), ChatLayout.unscaledWidth(bounds.width(), ChatLayout.META_SCALE))) {
            if (y + ChatUi.lineHeight(font, ChatLayout.META_SCALE) > bounds.y() + bounds.height()) break;
            ChatUi.text(g, font, line, bounds.x(), y, ChatLayout.META_SCALE, FontPalette.subtle()); y += ChatUi.lineHeight(font, ChatLayout.META_SCALE) + 1;
        }
    }
    private void button(GuiGraphics g, Font font, ChatLayout.Rect b, String key, int mx, int my) {
        ChatUi.glass(g, ChatGlass.Surface.BUTTON, b.x(), b.y(), b.width(), b.height(), request == null && b.contains(mx, my));
        ChatUi.buttonText(g, font, ChatUi.truncate(font, Component.translatable(key).getString(), b.width() - 2, ChatLayout.META_SCALE),
                b.x() + b.width() / 2f, b.y() + b.height() / 2f, ChatLayout.META_SCALE, request == null ? ChatUi.accent() : FontPalette.dim());
    }
    boolean click(double mx, double my, int button) {
        if (button != 0 || request != null) return true;
        for (ChatGroupNotices.Row row : hits) {
            if (!row.accept().contains(mx, my) && !row.decline().contains(mx, my)) continue;
            if (GroupClientCache.invitations().stream().noneMatch(i -> i.id().equals(row.invitation().id()))) {
                status = GroupStatus.INVITATION_GONE.key(); hits = ImmutableList.of(); return true;
            }
            request = UUID.randomUUID(); group = row.invitation().group(); accepting = row.accept().contains(mx, my);
            started = System.currentTimeMillis(); status = ""; GroupClientCache.track(request);
            send.accept(new GroupCommand.RespondInvitation(request, group, row.invitation().id(), accepting)); return true;
        }
        return true;
    }
    boolean mouseScrolled(double mx, double my, double amount) {
        if (area == null || !area.contains(mx, my) || amount == 0) return false;
        if (request != null) return true;
        int old = scroll; scroll = Math.max(0, Math.min(Math.max(0, GroupClientCache.invitations().size() - area.height() / ROW_HEIGHT), scroll + (amount > 0 ? -1 : 1)));
        if (old != scroll) hits = ImmutableList.of(); return old != scroll;
    }
}
