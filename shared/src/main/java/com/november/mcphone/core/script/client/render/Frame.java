package com.november.mcphone.core.script.client.render;

import com.november.mcphone.api.client.ui.PhoneCanvas;
import com.november.mcphone.core.script.layout.LayoutNode;
import com.november.mcphone.core.script.layout.TextMeasure;
import com.november.mcphone.core.script.layout.UiState;
import com.november.mcphone.core.script.pkg.AppPackage;

import java.util.Set;

/**
 * 一帧里画与悬停共用的东西（施工方案 §8.6）。每帧开头由页面新建一个，别跨帧留着：
 * 悬停链在构造时按这一帧的鼠标算好，走的是与点击同一个 {@link HitTest#pick}。
 */
public final class Frame {

    final PhoneCanvas canvas;
    final TextMeasure tm;
    final UiState state;
    final AppPackage pkg;
    final LayoutNode pressed;
    private final Set<LayoutNode> hovered;

    /**
     * @param pkg     image 取贴图用，可为 null
     * @param pressed 这一帧要叠按下色的按钮，没有时为 null
     */
    public Frame(LayoutNode root, PhoneCanvas canvas, UiState state, AppPackage pkg, LayoutNode pressed) {
        this.canvas = canvas;
        this.tm = new FontMeasure(canvas.font());
        this.state = state;
        this.pkg = pkg;
        this.pressed = pressed;
        this.hovered = canvas.hoveredContent()
                ? HitTest.hoverChain(root, canvas.x(), canvas.y(), canvas.width(), canvas.height(),
                        canvas.mouseX(), canvas.mouseY(), tm)
                : Set.of();
    }

    /** 这个节点是鼠标下那个可交互节点，或者是它的祖先。 */
    public boolean hovered(LayoutNode n) {
        return hovered.contains(n);
    }
}
