package com.november.mcphone.core.client;

import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.platform.client.PhoneToastBase;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.UUID;

/**
 * 收到消息时右上角弹出的通知。
 *
 * 为什么用通知而不是往聊天框里发一句
 *
 * 聊天框是公共场所：玩家可能装了聊天管理或过滤类模组，也可能正被别的
 * 模组刷屏，消息提醒混进去容易被淹没，还会永久占着聊天记录。
 *
 * 通知是原版专为这件事准备的机制——有独立队列、自动排队、自动消失，
 * 连"叮"的一声都是原版 {@code Visibility.SHOW} 自带的，
 * 不必自己播音效。
 *
 * 同一个人连发要合并
 *
 * 原版通知区只有 5 个槽位，满了就排队。一个人连发五条就能把别的通知
 * 全挤掉，所以同一个发信人只占一条：后来的消息更新这一条的正文与计数，
 * 并把停留时间重新计起。
 *
 * 合并靠 {@link #getToken()} 返回发信人 UUID，
 * 原版那个通知区管理器据此找到已在显示或还在排队的那一条。
 *
 * <h2>为什么这个类不直接 implements Toast</h2>
 *
 * 老那三支上 {@code Toast} 只有一个抽象方法 {@code render}，【画】与【还留不留】在这一个
 * 方法里一起办完；26.x 把它拆成了两步 —— 管理器每帧先问一次「该不该继续显示」，
 * 之后才让这条通知自己画。子类没法同时学两个形状，所以这里只写两个中立方法
 * （{@code drawToast} 与 {@code shouldStillShow}），原版那个接口在这一支上长什么样、
 * 由每支一份的 {@link PhoneToastBase} 自己去覆写。判据同 {@code platform.client.Screens}：
 * 形变发生在【覆写点】上，门面与改名表都够不着。
 */
public final class PhoneToast extends PhoneToastBase {

    /** 与原版槽位一致，照这个尺寸画才不会和别的模组的通知错位 */
    private static final int WIDTH = 160;
    private static final int HEIGHT = 32;

    /** 头像边长。24 ＝ 皮肤头部 8×8 的三倍，整数倍放大才不糊 */
    private static final int AVATAR_SIZE = 24;

    private static final int PAD = 4;

    /** 停留时长。比原版通知略长一点：消息比成就更值得看清楚 */
    private static final long DISPLAY_TIME_MS = 5000L;

    /** 预览最多留几个字，够填满通知那一行就行 */
    private static final int PREVIEW_MAX_CHARS = 64;

    // ---- 颜色（贴图缺失时的兜底） ----
    private static final int COLOR_BG = PhoneTheme.COLOR_TOAST_BG;
    private static final int COLOR_BORDER = PhoneTheme.COLOR_TOAST_BORDER;
    private static final int COLOR_NAME = PhoneTheme.FONT_COLOR_TOAST_TITLE;
    private static final int COLOR_TEXT = PhoneTheme.FONT_COLOR_TOAST;
    private static final int COLOR_BADGE_BG = PhoneTheme.COLOR_UNREAD_BADGE;

    private final UUID sender;
    private final String senderName;

    private String text;

    /** 合并进来的条数，1 表示只有一条，不显示角标 */
    private int count = 1;

    /** 最后一次更新的时刻，用来把停留时间重新计起 */
    private long lastUpdateMs = -1L;

    private boolean changed = true;

    public PhoneToast(UUID sender, String senderName, String text) {
        this.sender = sender;
        this.senderName = senderName;
        this.text = text;
    }

    /**
     * 同一个人又来消息了：换成最新一条，条数加一，停留时间重新计起。
     *
     * 显示最新一条而不是最早那条：玩家瞥一眼通知想知道的是"他刚说了
     * 什么"，不是三十秒前说过什么。
     */
    public void addMessage(String newText) {
        this.text = newText;
        this.count++;
        this.changed = true;
    }

    /** 发信人 UUID —— 原版据此找到同一个人的通知来合并 */
    @Override
    public Object getToken() {
        return sender;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    protected void drawToast(GuiGraphics g, Font font, long timeSinceLastVisible) {
        // 底：贴图优先，没有贴图就画纯色加一圈边
        if (!PhoneSkin.draw(g, PhoneSkin.Element.TOAST_BG, 0, 0, WIDTH, HEIGHT)) {
            g.fill(0, 0, WIDTH, HEIGHT, COLOR_BG);
            g.renderOutline(0, 0, WIDTH, HEIGHT, COLOR_BORDER);
        }

        PlayerAvatar.draw(g, sender, PAD, (HEIGHT - AVATAR_SIZE) / 2, AVATAR_SIZE);

        int textX = PAD + AVATAR_SIZE + PAD;
        int textW = WIDTH - textX - PAD;

        // 有多条时右上角留出角标的位置，名字不能压到它
        int badgeW = count > 1 ? font.width(countLabel()) + 4 : 0;
        g.drawString(font, GuiUtil.truncate(font, senderName, textW - badgeW - 2),
                textX, 7, COLOR_NAME, false);
        g.drawString(font, GuiUtil.truncate(font, text, textW), textX, 18, COLOR_TEXT, false);

        if (count > 1) {
            int badgeX = WIDTH - PAD - badgeW;
            // 与会话列表的未读角标共用贴图，换肤时两处一致
            PhoneSkin.drawOrFill(g, PhoneSkin.Element.UNREAD_BADGE,
                    badgeX, 6, badgeW, font.lineHeight + 1, COLOR_BADGE_BG);
            g.drawString(font, countLabel(), badgeX + 2, 7, COLOR_NAME, false);
        }
    }

    /**
     * 这一条还该留着，还是该收起来。
     *
     * <p>【有新消息并进来就把停留计时重置】放在这一半里，不放在 {@code drawToast} 里：
     * 26.x 上「决定去留」与「画」是分开的两次回调，而且先决定、后画。重置要留在画的那一半，
     * 那一帧就已经按旧的计时决定完了 —— 症状是并进来一条新消息时通知先往外滑半格再回来。
     */
    @Override
    protected boolean shouldStillShow(long timeSinceLastVisible, double displayMultiplier) {
        if (changed) {
            lastUpdateMs = timeSinceLastVisible;
            changed = false;
        }
        // 原版按显示时长的倍率缩放，玩家在设置里调过通知时间就该跟着变
        return timeSinceLastVisible - lastUpdateMs < DISPLAY_TIME_MS * displayMultiplier;
    }

    /** 超过 99 就显示 99+，否则一个三位数会把角标撑变形 */
    private String countLabel() {
        return count > 99 ? "99+" : String.valueOf(count);
    }

    /**
     * 通知里显示的消息预览。
     *
     * 先按字符截一刀：通知只有一行的地方，一条 256 字的消息里 240 字
     * 都画不出来，何必让 truncate 逐字去量宽度。真正的按像素截断仍由
     * 渲染时的 truncate 负责，这里只是别让它做无用功。
     */
    public static String preview(ChatMessage message) {
        String raw = message.body().preview().getString();
        return raw.length() <= PREVIEW_MAX_CHARS ? raw : raw.substring(0, PREVIEW_MAX_CHARS);
    }

    /** 拿不到名字时的兜底显示，与会话界面同一个规矩 */
    public static String fallbackName(UUID id) {
        return id.toString().substring(0, 8);
    }
}
