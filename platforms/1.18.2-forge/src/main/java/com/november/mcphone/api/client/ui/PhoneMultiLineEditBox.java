package com.november.mcphone.api.client.ui;

import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.platform.client.PhoneGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Multiline editor backed by the same text and clipboard helper as the 1.18.2 book editor. */
public final class PhoneMultiLineEditBox {
    private static final int PAD = 4;
    private final Font font;
    private final Component placeholder;
    private final TextFieldHelper textField;
    private List<Line> lines;
    private String value = "";
    private int x, y, width, height;
    private int limit = Integer.MAX_VALUE;
    private int firstLine;
    private boolean focused;
    private boolean dragging;
    private long caretStart;

    private record Line(int start, int end) {}

    public PhoneMultiLineEditBox(Font font, int x, int y, int width, int height,
                                 Component placeholder, Component message) {
        this.font = font;
        this.placeholder = placeholder;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        Minecraft mc = Minecraft.getInstance();
        textField = new TextFieldHelper(() -> value, this::updateText,
                // The vanilla clipboard getter strips line breaks, so read directly here.
                () -> mc.keyboardHandler.getClipboard().replace("\r\n", "\n").replace('\r', '\n'),
                TextFieldHelper.createClipboardSetter(mc), text -> text.length() <= limit);
    }

    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setWidth(int width) {
        if (this.width == width) return;
        this.width = width;
        lines = null;
        clampScroll();
    }
    public void setHeight(int height) {
        if (this.height == height) return;
        this.height = height;
        clampScroll();
    }
    public void setCharacterLimit(int limit) {
        this.limit = Math.max(0, limit);
        if (value.length() > this.limit) setValue(value);
    }
    public void setFocused(boolean focused) {
        if (this.focused == focused) return;
        this.focused = focused;
        if (!focused) dragging = false;
        caretStart = System.currentTimeMillis();
    }
    public String getValue() { return value; }
    public void setValue(String text) {
        int end = Math.min(text.length(), limit);
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        updateText(text.substring(0, end));
        textField.setCursorToEnd();
        revealCursor();
    }

    private void updateText(String text) {
        value = text;
        lines = null;
    }

    private List<Line> lines() {
        if (lines == null) {
            List<Line> wrapped = new ArrayList<>();
            font.getSplitter().splitLines(value, Math.max(1, width - 2 * PAD - 3), Style.EMPTY, false,
                    (style, start, end) -> wrapped.add(new Line(start, end)));
            if (wrapped.isEmpty() || value.endsWith("\n")) wrapped.add(new Line(value.length(), value.length()));
            lines = List.copyOf(wrapped);
        }
        return lines;
    }

    private int visibleLines() { return Math.max(1, (height - 2 * PAD) / font.lineHeight); }
    private int maxScroll() { return Math.max(0, lines().size() - visibleLines()); }
    private void clampScroll() { firstLine = Math.max(0, Math.min(firstLine, maxScroll())); }
    private int lineAt(int position) {
        var wrapped = lines();
        for (int i = 1; i < wrapped.size(); i++) {
            if (position < wrapped.get(i).start()) return i - 1;
        }
        return wrapped.size() - 1;
    }
    private int advance(Line line, int position) {
        return font.width(value.substring(line.start(), Math.max(line.start(), Math.min(line.end(), position))));
    }
    private int positionAt(int row, int offset) {
        Line line = lines().get(Math.max(0, Math.min(lines().size() - 1, row)));
        String text = value.substring(line.start(), line.end());
        if (offset <= 0) return line.start();
        return line.start() + font.getSplitter().plainIndexAtWidth(text, offset, Style.EMPTY);
    }
    private void revealCursor() {
        int row = lineAt(textField.getCursorPos());
        if (row < firstLine) firstLine = row;
        if (row >= firstLine + visibleLines()) firstLine = row - visibleLines() + 1;
        clampScroll();
        caretStart = System.currentTimeMillis();
    }

    public void render(PhoneGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (width < 2 * PAD || height < 2 * PAD) return;
        graphics.fill(x, y, x + width, y + height, 0xC0000000);
        graphics.renderOutline(x, y, width, height, focused ? 0xFFFFFFFF : 0xFF888888);
        int left = x + PAD;
        int top = y + PAD;
        int cursor = textField.getCursorPos();
        int row = lineAt(cursor);
        int selectionStart = Math.min(cursor, textField.getSelectionPos());
        int selectionEnd = Math.max(cursor, textField.getSelectionPos());
        clampScroll();
        GuiUtil.clipped(graphics, x + 1, y + 1, x + width - 1, y + height - 1, () -> {
            if (value.isEmpty()) graphics.drawString(font, placeholder, left, top, 0xFFA0A0A0, false);
            for (int i = firstLine; i < Math.min(lines().size(), firstLine + visibleLines()); i++) {
                Line line = lines().get(i);
                int baseline = top + (i - firstLine) * font.lineHeight;
                int nextStart = i + 1 < lines().size() ? lines().get(i + 1).start() : line.end();
                if (selectionStart < selectionEnd && selectionStart < nextStart && selectionEnd > line.start()) {
                    int start = advance(line, selectionStart);
                    int end = advance(line, selectionEnd) + (selectionEnd > line.end() ? 3 : 0);
                    graphics.fill(left + start, baseline, left + end, baseline + font.lineHeight, 0xFF305F9E);
                }
                graphics.drawString(font, value.substring(line.start(), line.end()), left, baseline, 0xFFFFFFFF, false);
                if (focused && i == row && (System.currentTimeMillis() - caretStart) % 1000 < 500) {
                    int caret = left + advance(line, cursor);
                    graphics.fill(caret, baseline, caret + 1, baseline + font.lineHeight, 0xFFFFFFFF);
                }
            }
        });
        if (maxScroll() > 0) {
            int track = height - 2;
            int thumb = Math.min(track, Math.max(6, track * visibleLines() / lines().size()));
            int topY = y + 1 + (track - thumb) * firstLine / maxScroll();
            graphics.fill(x + width - 3, topY, x + width - 1, topY + thumb, 0xFFB0B0B0);
        }
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
    private void selectAt(double mouseX, double mouseY, boolean extend) {
        int row = firstLine + (int) Math.floor((mouseY - y - PAD) / font.lineHeight);
        textField.setCursorPos(positionAt(row, (int) (mouseX - x - PAD)), extend);
        revealCursor();
    }
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        setFocused(contains(mouseX, mouseY));
        if (!focused) return false;
        dragging = true;
        selectAt(mouseX, mouseY, Screen.hasShiftDown());
        return true;
    }
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (!dragging || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        selectAt(mouseX, mouseY, true);
        return true;
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!contains(mouseX, mouseY) || scrollY == 0) return false;
        firstLine -= (int) Math.copySign(Math.max(1, Math.round(Math.abs(scrollY))), scrollY);
        clampScroll();
        return true;
    }
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!focused) return false;
        int cursor = textField.getCursorPos();
        int row = lineAt(cursor);
        Line line = lines().get(row);
        boolean extend = Screen.hasShiftDown();
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> textField.insertText("\n");
            case GLFW.GLFW_KEY_UP -> textField.setCursorPos(positionAt(row - 1, advance(line, cursor)), extend);
            case GLFW.GLFW_KEY_DOWN -> textField.setCursorPos(positionAt(row + 1, advance(line, cursor)), extend);
            case GLFW.GLFW_KEY_HOME -> textField.setCursorPos(Screen.hasControlDown() ? 0 : line.start(), extend);
            case GLFW.GLFW_KEY_END -> textField.setCursorPos(Screen.hasControlDown() ? value.length() : line.end(), extend);
            default -> { if (!textField.keyPressed(key)) return false; }
        }
        revealCursor();
        return true;
    }
    public boolean charTyped(char c, int modifiers) {
        if (!focused || !net.minecraft.SharedConstants.isAllowedChatCharacter(c)) return false;
        textField.charTyped(c);
        revealCursor();
        return true;
    }
}
