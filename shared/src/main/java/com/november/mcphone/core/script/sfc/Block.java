package com.november.mcphone.core.script.sfc;

/**
 * 一个顶层块。{@code startLine} 是块内容第一行在原文件里的行号（1-based）：块内第 k 行 = 原文件第 startLine + k - 1 行，
 * 错误行号映射全靠它（§9.8）。内容按行拼回，每行以 \n 结尾，行尾的 \r 已去掉。
 */
public final class Block {
    private final String name;
    private final int startLine;
    private final String content;

    public Block(String name, int startLine, String content) {
        this.name = name;
        this.startLine = startLine;
        this.content = content;
    }

    public String name() { return name; }

    public int startLine() { return startLine; }

    public String content() { return content; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Block)) return false;
        Block that = (Block) other;
        return java.util.Objects.equals(this.name, that.name)
                && this.startLine == that.startLine
                && java.util.Objects.equals(this.content, that.content);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(startLine);
        hash = 31 * hash + java.util.Objects.hashCode(content);
        return hash;
    }

    @Override public String toString() { return "Block[name=" + name + ", startLine=" + startLine + ", content=" + content + "]"; }

}
