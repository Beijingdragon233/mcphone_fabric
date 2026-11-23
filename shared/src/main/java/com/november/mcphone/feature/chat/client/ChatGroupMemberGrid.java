package com.november.mcphone.feature.chat.client;

import com.google.common.collect.ImmutableList;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.PlayerAvatar;
import com.november.mcphone.feature.chat.group.GroupMember;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 成员网格的尺寸、滚动和命中共用一份布局；没有邀请权限或网络逻辑。 */
final class ChatGroupMemberGrid {
    static final int ROW_HEIGHT = 28;
    static final class Cell {
        private final int member;
        private final ChatLayout.Rect bounds;

        Cell(int member, ChatLayout.Rect bounds) {
            this.member = member;
            this.bounds = bounds;
        }

        public int member() { return member; }

        public ChatLayout.Rect bounds() { return bounds; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Cell)) return false;
            Cell that = (Cell) other;
            return this.member == that.member
                    && java.util.Objects.equals(this.bounds, that.bounds);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(member);
            hash = 31 * hash + java.util.Objects.hashCode(bounds);
            return hash;
        }

        @Override public String toString() { return "Cell[member=" + member + ", bounds=" + bounds + "]"; }
 boolean invite() { return member < 0; } }
    private List<Cell> cells = ImmutableList.of();
    private List<GroupMember> renderedMembers=ImmutableList.of();
    private ChatLayout.Rect area;
    private int offset, maximum;
    static int columns(int width) { return Math.max(1, Math.min(5, width / 24)); }
    static float avatarX(ChatLayout.Rect cell) { return cell.x() + (cell.width() - 16) / 2f; }
    static int previewCount(int width) { return columns(width) * 2 - 1; }
    static List<Cell> layout(ChatLayout.Rect area, int members, boolean expanded, int offset) {
        int columns = columns(area.width());
        int rows = Math.max(0, area.height() / ROW_HEIGHT);
        int shown = expanded ? members : Math.min(members, Math.max(0, columns * Math.min(2, rows) - 1));
        int max = Math.max(0, (shown + 1 + columns - 1) / columns - rows);
        int first = Math.max(0, Math.min(offset, max)) * columns;
        ArrayList<ChatGroupMemberGrid.Cell> out = new ArrayList<Cell>();
        for (int i = first; i <= shown && i < first + rows * columns; i++) {
            int col = i % columns, row = (i - first) / columns;
            int x = area.x() + col * area.width() / columns;
            int end = area.x() + (col + 1) * area.width() / columns;
            out.add(new Cell(i == shown ? -1 : i, new ChatLayout.Rect(x, area.y() + row * ROW_HEIGHT, end - x, ROW_HEIGHT)));
        }
        return ImmutableList.copyOf(out);
    }
    void render(GuiGraphics g, Font font, List<GroupMember> members, ChatLayout.Rect next, boolean expanded, int mx, int my) {
        area = next; renderedMembers=ImmutableList.copyOf(members);
        int shown = expanded ? members.size() : Math.min(members.size(), Math.max(0, columns(next.width()) * Math.min(2, next.height() / ROW_HEIGHT) - 1));
        maximum = Math.max(0, (shown + 1 + columns(next.width()) - 1) / columns(next.width()) - next.height() / ROW_HEIGHT);
        offset = Math.max(0, Math.min(offset, maximum)); cells = layout(next, members.size(), expanded, offset);
        for (ChatGroupMemberGrid.Cell cell : cells) {
            ChatLayout.Rect b = cell.bounds(); int y = b.y() + 1;
            String label = cell.invite() ? Component.translatable("mcphone.chat.group.invite").getString()
                    : members.get(cell.member()).name();
            // 列宽可能是奇数，头像与文字都按同一浮点中心定位，不向左截掉半像素。
            g.pose().pushPose();
            try {
                g.pose().translate(avatarX(b), y, 0);
                if (cell.invite()) {
                    ChatUi.glass(g, ChatGlass.Surface.BUTTON, 0, 0, 16, 16, b.contains(mx, my));
                    ChatUi.sizedIcon(g, ChatUi.Icon.ADD, 4, 4, 8,
                            b.contains(mx, my) ? ChatUi.accent() : FontPalette.body());
                } else PlayerAvatar.draw(g, members.get(cell.member()).id(), 0, 0, 16);
            } finally { g.pose().popPose(); }
            ChatUi.buttonText(g, font, ChatUi.truncate(font, label, b.width() - 2, ChatLayout.META_SCALE),
                    b.x() + b.width() / 2f, y + 21, ChatLayout.META_SCALE, FontPalette.subtle());
        }
    }
    java.util.UUID memberClicked(double mx,double my,int button) {
        if (button!=0) return null;
        for (ChatGroupMemberGrid.Cell cell:cells) if (!cell.invite() && cell.bounds().contains(mx,my)) return renderedMembers.get(cell.member()).id();
        return null;
    }
    boolean inviteClicked(double mx, double my, int button) {
        return button == 0 && cells.stream().anyMatch(c -> c.invite() && c.bounds().contains(mx, my));
    }
    boolean scroll(double mx, double my, double amount) {
        if (area == null || !area.contains(mx, my) || amount == 0 || maximum == 0) return false;
        int old = offset; offset = Math.max(0, Math.min(maximum, offset + (amount > 0 ? -1 : 1))); return old != offset;
    }
    void close() { cells = ImmutableList.of(); renderedMembers=ImmutableList.of(); area = null; offset = maximum = 0; }
}
