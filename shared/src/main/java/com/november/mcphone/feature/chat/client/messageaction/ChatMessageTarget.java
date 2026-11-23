package com.november.mcphone.feature.chat.client.messageaction;

import java.util.UUID;

/** 菜单打开时捕获不可变目标，不引用消息绘制器、输入框或可变消息下标。 */
final class ChatMessageTarget {
    private final UUID peer;
    private final UUID messageId;
    private final String fullText;
    private final String selectedText;

    ChatMessageTarget(UUID peer, UUID messageId, String fullText, String selectedText) {
        this.peer = peer;
        this.messageId = messageId;
        this.fullText = fullText;
        this.selectedText = selectedText;
    }

    public UUID peer() { return peer; }

    public UUID messageId() { return messageId; }

    public String fullText() { return fullText; }

    public String selectedText() { return selectedText; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChatMessageTarget)) return false;
        ChatMessageTarget that = (ChatMessageTarget) other;
        return java.util.Objects.equals(this.peer, that.peer)
                && java.util.Objects.equals(this.messageId, that.messageId)
                && java.util.Objects.equals(this.fullText, that.fullText)
                && java.util.Objects.equals(this.selectedText, that.selectedText);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(peer);
        hash = 31 * hash + java.util.Objects.hashCode(messageId);
        hash = 31 * hash + java.util.Objects.hashCode(fullText);
        hash = 31 * hash + java.util.Objects.hashCode(selectedText);
        return hash;
    }

    @Override public String toString() { return "ChatMessageTarget[peer=" + peer + ", messageId=" + messageId + ", fullText=" + fullText + ", selectedText=" + selectedText + "]"; }

    boolean copyable() { return fullText != null; }
    String copiedText() { return selectedText.isEmpty() ? fullText : selectedText; }
}
