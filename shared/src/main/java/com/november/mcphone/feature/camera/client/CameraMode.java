package com.november.mcphone.feature.camera.client;

import com.november.mcphone.platform.client.CameraGui;
import net.minecraft.client.Minecraft;

/**
 * 相机模式状态机（纯客户端）。相机不是 GUI，是覆盖在游戏画面上的模式。
 *
 * 拍照要分帧，否则取景框会被拍进照片：按键置 pendingCapture → 渲染帧跳过取景框并置
 * cleanFrameReady → 下一 tick 抓取（期间的每一帧都被抑制，抓到的必然干净）。
 * 抓取放在 tick 而不是渲染中：Screenshot.grab 会 bindTexture 动 RenderSystem，与原版 F2 时机一致。
 */
public final class CameraMode {

    private static boolean active = false;

    /** 进入相机前玩家原本的 HUD 隐藏状态，退出时原样还回去。读写都走 CameraGui */
    private static boolean savedHideGui = false;

    private static long enteredAtMs = 0L;

    private static boolean pendingCapture = false;
    private static boolean cleanFrameReady = false;

    /** 最近一次拍照完成的时刻，用于白闪 */
    private static long flashAtMs = 0L;

    private CameraMode() {}

    public static boolean isActive() { return active; }

    public static void enter() {
        if (active) return;
        Minecraft mc = Minecraft.getInstance();

        active = true;
        savedHideGui = CameraGui.hidden(mc);
        // 取景时该不该把原版 HUD 藏起来，【各支的答案不一样】—— 理由见 CameraGui；
        // 怎么写也不统一：1.20.1 / 1.21.1 是直接赋值，26.x 那边只剩一个 toggle
        CameraGui.setHidden(mc, CameraGui.hideGuiWhileFraming());
        enteredAtMs = System.currentTimeMillis();
        pendingCapture = false;
        cleanFrameReady = false;
    }

    public static void exit() {
        if (!active) return;

        // 还原而不是无脑置 false：玩家可能本来就自己按了 F1
        CameraGui.setHidden(Minecraft.getInstance(), savedHideGui);
        active = false;
        pendingCapture = false;
        cleanFrameReady = false;
    }

    public static void requestCapture() {
        if (!active || pendingCapture) return;
        pendingCapture = true;
        cleanFrameReady = false;
    }

    /** 取景框此刻是否应被抑制（拍照期间不能入镜） */
    public static boolean suppressOverlay() { return pendingCapture; }

    /** 由渲染层调用，告知本帧未绘制取景框 */
    public static void markCleanFrame() {
        if (pendingCapture) cleanFrameReady = true;
    }

    public static boolean shouldGrabNow() { return pendingCapture && cleanFrameReady; }

    /** 抓取完成后调用，恢复取景框并触发白闪 */
    public static void finishCapture() {
        pendingCapture = false;
        cleanFrameReady = false;
        flashAtMs = System.currentTimeMillis();
    }

    public static long getEnteredAtMs() { return enteredAtMs; }
    public static long getFlashAtMs() { return flashAtMs; }
}
