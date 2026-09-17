package com.november.mcphone.feature.store.client;

import com.november.mcphone.api.client.store.AppInfo;
import com.november.mcphone.api.client.store.IAppSource;
import com.november.mcphone.api.cost.ICost;
import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.PhoneScreenRegistry;
import com.november.mcphone.core.client.PhoneSkin;
import com.november.mcphone.core.client.PhoneTheme;
import com.november.mcphone.core.script.pkg.SigCopy;
import com.november.mcphone.feature.store.AppPriceRegistry;
import com.november.mcphone.feature.store.client.AppSourceRegistry;
import com.november.mcphone.feature.store.net.StoreClientCache;
import com.november.mcphone.core.client.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 应用详情页：一个 App 的介绍、价格与唯一的按钮（购买/买不起/下载/已安装 四态）。
 * "买过了"以服务端账本为准，{@link StoreClientCache} 只是画按钮用的镜像；
 * 同步没回来之前按钮是"加载中"，别把"还不知道"画成"没买过"。
 */
public final class AppDetail {

    private static final int PAD = 6;
    private static final int BIG_ICON = 32;
    private static final int BUTTON_H = 16;

    private AppInfo info;

    /** 渲染时算出来，点击时复用 */
    private int btnX, btnY, btnW;
    private boolean btnHovered;
    private boolean btnEnabled;

    /** 二次确认框的位置；没画出来时 {@code confirmBoxY} 是 -1。 */
    private int confirmBoxX, confirmBoxY = -1;

    private Component message = null;

    /** 上一帧的按钮状态：状态一变就清提示，没有计时器 */
    private State lastState = null;

    /** 请求退回商店首页，等 PhoneScreen 来取 */
    private boolean backRequest = false;

    /** 装成功了，商店首页需要刷新列表 */
    private boolean installedRequest = false;

    /**
     * 「作者密钥变了」那一档要输入的东西（§12.4：<b>要输入确认短语，不是点一下</b>）。
     * 用 {@code char[]} 没必要 —— 这不是口令，是一串公开的指纹。
     */
    private String typedPhrase = "";

    /** 二次确认那个框勾了没有。换一个 App 就清掉。 */
    private boolean confirmTicked;

    public void open(AppInfo target) {
        this.info = target;
        this.message = null;
        this.lastState = null;
        this.backRequest = false;
        this.installedRequest = false;
        this.typedPhrase = "";
        this.confirmTicked = false;
    }

    public boolean consumeBackRequest() {
        boolean r = backRequest;
        backRequest = false;
        return r;
    }

    public boolean consumeInstalledRequest() {
        boolean r = installedRequest;
        installedRequest = false;
        return r;
    }

    private enum State { LOADING, BLOCKED, BUY, CANT_AFFORD, DOWNLOAD, INSTALLED }

    private State state() {
        if (info == null) return State.LOADING;

        if (PhoneScreenRegistry.isInstalled(info.id())) return State.INSTALLED;

        // 排在价钱之前：装不了的东西不该先问玩家买不买得起（§23.4）
        if (info.blockedReason() != null) return State.BLOCKED;

        ICost price = AppPriceRegistry.priceOf(info.id());
        if (price == ICost.FREE) return State.DOWNLOAD;

        if (!StoreClientCache.isSynced()) return State.LOADING;
        if (StoreClientCache.has(info.id())) return State.DOWNLOAD;

        var player = Minecraft.getInstance().player;
        if (player != null && !price.canAfford(player)) return State.CANT_AFFORD;
        return State.BUY;
    }

    private String labelOf(State s) {
        return switch (s) {
            case LOADING -> Component.translatable("mcphone.store.loading").getString();
            case BLOCKED -> info.blockedReason().getString();
            case BUY -> Component.translatable("mcphone.store.buy").getString();
            case CANT_AFFORD -> Component.translatable("mcphone.store.cant_afford").getString();
            case DOWNLOAD -> Component.translatable("mcphone.store.install").getString();
            case INSTALLED -> Component.translatable("mcphone.store.installed_label").getString();
        };
    }

    public void render(GuiGraphics g, int phoneLeft, int phoneTop,
                       int screenW, int screenH, int statusH, int navH,
                       int mouseX, int mouseY, Font font) {

        int x = phoneLeft + PAD;
        int y = phoneTop + statusH + 4;
        int w = screenW - PAD * 2;
        int bottom = phoneTop + screenH - navH;

        if (info == null) {
            g.drawString(font, Component.translatable("mcphone.store.empty").getString(),
                    x, y, FontPalette.subtle(), false);
            return;
        }

        // 状态一变说明上一次操作有结果了，撤掉"正在购买…"
        State s = state();
        if (lastState != null && s != lastState) message = null;
        lastState = s;

        if (info.iconTexture() != null) {
            GuiUtil.drawTexture(g, info.iconTexture(), x, y, BIG_ICON, BIG_ICON, BIG_ICON, BIG_ICON);
        } else {
            g.fill(x, y, x + BIG_ICON, y + BIG_ICON, PhoneTheme.COLOR_BUTTON_DISABLED);
        }

        int textX = x + BIG_ICON + 5;
        int textW = w - BIG_ICON - 5;
        g.drawString(font, GuiUtil.truncate(font, info.displayName().getString(), textW),
                textX, y + 2, FontPalette.title(), false);

        String meta = info.author() == null || info.author().isBlank()
                ? "v" + info.version()
                : info.author() + " · v" + info.version();
        g.drawString(font, GuiUtil.truncate(font, meta, textW),
                textX, y + 2 + font.lineHeight + 2, FontPalette.subtle(), false);

        y += BIG_ICON + 6;

        g.fill(x, y, x + w, y + 1, PhoneTheme.COLOR_DIVIDER);
        y += 4;

        int bodyBottom = bottom - BUTTON_H - font.lineHeight - 8;
        String desc = info.description();
        if (desc == null || desc.isBlank()) {
            desc = Component.translatable("mcphone.store.no_description").getString();
        }
        for (var line : font.split(Component.literal(desc), w)) {
            if (y + font.lineHeight > bodyBottom) break;
            g.drawString(font, line, x, y, FontPalette.body(), false);
            y += font.lineHeight + 1;
        }

        y = renderSignature(g, font, x, y, w, bodyBottom);

        if (message != null) {
            g.drawString(font, GuiUtil.truncate(font, message.getString(), w),
                    x, bodyBottom, FontPalette.notice(), false);
        }

        ICost price = AppPriceRegistry.priceOf(info.id());
        String priceText = price == ICost.FREE
                ? Component.translatable("mcphone.store.free").getString()
                : price.describe().getString();
        int priceY = bottom - BUTTON_H - font.lineHeight - 3;
        g.drawString(font, GuiUtil.truncate(font, priceText, w), x, priceY,
                price == ICost.FREE ? FontPalette.subtle() : FontPalette.price(),
                false);

        btnX = x;
        btnY = bottom - BUTTON_H - 1;
        btnW = w;
        // 【签名无效】那一档由 blockedReason 走 BLOCKED，本来就画不出按钮；
        // 【作者密钥变了】要抄对新指纹才放行 —— 不然按钮画灰，不给"点一下就走"
        btnEnabled = (s == State.BUY || s == State.DOWNLOAD) && signatureSatisfied();
        btnHovered = btnEnabled && mouseX >= btnX && mouseX <= btnX + btnW
                && mouseY >= btnY && mouseY <= btnY + BUTTON_H;

        if (btnEnabled) {
            // 悬停那一档要传下去：这个位上一旦有了贴图，"换个颜色"就再也看不出来了，
            // 得让 PhoneSkin 整张提亮（1.9.2 补齐自带贴图之后这里就是这么坏掉的）
            PhoneSkin.drawOrFill(g, PhoneSkin.Element.STORE_BUTTON, btnX, btnY, btnW, BUTTON_H,
                    btnHovered ? PhoneTheme.COLOR_BUTTON_HOVER : PhoneTheme.COLOR_BUTTON,
                    btnHovered);
        } else {
            PhoneSkin.drawOrFill(g, PhoneSkin.Element.STORE_BUTTON_DISABLED,
                    btnX, btnY, btnW, BUTTON_H, PhoneTheme.COLOR_BUTTON_DISABLED);
        }

        String label = labelOf(s);
        g.drawString(font, label,
                btnX + (btnW - font.width(label)) / 2,
                btnY + (BUTTON_H - font.lineHeight) / 2 + 1,
                btnEnabled ? PhoneTheme.FONT_COLOR_BUTTON : PhoneTheme.FONT_COLOR_BUTTON_DISABLED,
                false);
    }

    /**
     * 签名那一段（§12.4 / §12.5）。档位提示 + 作者指纹 + <b>每次都显示的 INSTALL_NOTE</b>。
     *
     * <p><b>这里不判签名</b>：状态是来源算好带过来的（{@link AppInfo#signature()}）。
     * 界面里再判一遍就会有两份判据。
     *
     * <p><b>「已签名」处不画对勾</b>：签名确认的是"谁做的、有没有被改过"，不是"内容安不安全"。
     * 一个绿色对勾会把那个区分抹掉，而 INSTALL_NOTE 正是为了讲清这件事才每次都显示。
     */
    private int renderSignature(GuiGraphics g, Font font, int x, int y, int w, int bodyBottom) {
        AppInfo.Signature sig = info.signature();
        if (sig == null) return y;                       // 内建 App 没有包，也就没有签名这一说

        y += 3;

        // 档位提示。参数按各档的文案填：密钥变了要新旧两个，陌生作者要指纹，已信任要名字与指纹
        String line = switch (sig.stateKey()) {
            case "mcphone.sig.key_changed" -> Component.translatable(sig.stateKey(),
                    String.valueOf(sig.previous()), String.valueOf(sig.fingerprint())).getString();
            case "mcphone.sig.unknown" -> Component.translatable(sig.stateKey(),
                    String.valueOf(sig.fingerprint())).getString();
            case "mcphone.sig.trusted" -> Component.translatable(sig.stateKey(),
                    info.author(), String.valueOf(sig.fingerprint())).getString();
            default -> Component.translatable(sig.stateKey()).getString();
        };
        int colour = sig.hardRejected() || "mcphone.sig.key_changed".equals(sig.stateKey())
                ? FontPalette.notice() : FontPalette.subtle();
        for (var l : font.split(Component.literal(line), w)) {
            if (y + font.lineHeight > bodyBottom) return y;
            g.drawString(font, l, x, y, colour, false);
            y += font.lineHeight;
        }

        // 指纹。【身份是它，不是 author】。未签名时这一格写「无」
        String fp = sig.fingerprint() == null
                ? Component.translatable("mcphone.sig.fingerprint_none").getString()
                : sig.fingerprint();
        if (y + font.lineHeight <= bodyBottom) {
            g.drawString(font, GuiUtil.truncate(font, fp, w), x, y, FontPalette.subtle(), false);
            y += font.lineHeight + 1;
        }

        // 要抄指纹的那一档：把输入框画出来
        if (sig.requiredPhrase() != null && y + font.lineHeight * 2 <= bodyBottom) {
            g.drawString(font, GuiUtil.truncate(font,
                            Component.translatable("mcphone.sig.confirm_prompt").getString(), w),
                    x, y, FontPalette.subtle(), false);
            y += font.lineHeight;
            g.fill(x, y, x + w, y + font.lineHeight + 2, PhoneTheme.COLOR_BUTTON_DISABLED);
            g.drawString(font, GuiUtil.truncate(font, typedPhrase, w - 4), x + 2, y + 2,
                    phraseTyped() ? FontPalette.body() : FontPalette.notice(), false);
            y += font.lineHeight + 4;
        }

        // INSTALL_NOTE —— 【每次安装都显示】。它把「签名 ≠ 安全」讲给玩家
        if (sig.needsConfirm() && y + font.lineHeight <= bodyBottom) {
            confirmBoxX = x;
            confirmBoxY = y;
            g.fill(x, y, x + font.lineHeight, y + font.lineHeight,
                    confirmTicked ? PhoneTheme.COLOR_BUTTON : PhoneTheme.COLOR_BUTTON_DISABLED);
            g.drawString(font, GuiUtil.truncate(font,
                            Component.translatable("mcphone.sig.confirm_tick").getString(),
                            w - font.lineHeight - 4),
                    x + font.lineHeight + 4, y + 1, FontPalette.body(), false);
            y += font.lineHeight + 4;
        } else {
            confirmBoxY = -1;
        }

        for (var l : font.split(Component.translatable("mcphone.sig.install_note"), w)) {
            if (y + font.lineHeight > bodyBottom) break;
            g.drawString(font, l, x, y, FontPalette.subtle(), false);
            y += font.lineHeight;
        }
        return y;
    }

    /** 不需要确认短语的档位恒为真；需要的那一档要抄对新指纹。 */
    /**
     * 界面这一层还差什么才让点安装。
     *
     * <p><b>这不是闸</b> —— 真正的闸在来源的 {@code install()} 里（它才是任何调用方都绕不开的
     * 那一道）。这里只是别让按钮看起来能点、点下去却被拒。
     */
    private boolean signatureSatisfied() {
        AppInfo.Signature sig = info == null ? null : info.signature();
        if (sig == null) return true;
        if (sig.requiredPhrase() != null) return phraseTyped();
        return !sig.needsConfirm() || confirmTicked;
    }

    /**
     * 输入的短语对不对。
     *
     * <p>比对本身<b>不在这里写第二份</b>：一份 {@code equalsIgnoreCase} 写在界面、
     * 另一份写在 {@code SigCopy}，两份迟早对不上。这里问的是那一份。
     */
    private boolean phraseTyped() {
        AppInfo.Signature sig = info.signature();
        return SigCopy.phraseAccepted(typedPhrase, sig.requiredPhrase());
    }

    /** 抄指纹用。只有那一档收键盘。 */
    public boolean charTyped(char c, int modifiers) {
        AppInfo.Signature sig = info == null ? null : info.signature();
        if (sig == null || sig.requiredPhrase() == null) return false;
        if (c < ' ' || typedPhrase.length() >= 64) return true;
        typedPhrase += c;
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        AppInfo.Signature sig = info == null ? null : info.signature();
        if (sig == null || sig.requiredPhrase() == null) return false;
        if (keyCode == 259 && !typedPhrase.isEmpty()) {          // backspace
            typedPhrase = typedPhrase.substring(0, typedPhrase.length() - 1);
            return true;
        }
        return false;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (info == null) return false;

        AppInfo.Signature sig = info.signature();
        if (sig != null && sig.needsConfirm() && confirmBoxY >= 0
                && mx >= confirmBoxX && mx <= confirmBoxX + 160
                && my >= confirmBoxY && my <= confirmBoxY + 12) {
            confirmTicked = !confirmTicked;
            return true;
        }

        if (!btnHovered) return false;

        switch (state()) {
            case BUY -> {
                // 只是提出请求：结果随同步包回来，按钮届时自己变成"下载"
                StoreClientCache.purchase(info.id());
                message = Component.translatable("mcphone.store.purchasing");
            }
            case DOWNLOAD -> install();
            default -> { }
        }
        return true;
    }

    /** 纯客户端：实现已随模组加载，"下载"只是把它加进已安装集合 */
    private void install() {
        IAppSource source = AppSourceRegistry.getSource(info.sourceId());
        if (source == null) {
            message = Component.translatable("mcphone.store.error.no_source",
                    info.sourceId().toString());
            return;
        }
        // 先把玩家输入的东西交给来源判。【放行的是来源，不是这个按钮】——
        // 界面只负责收集，判据那一份写在来源的 confirmSignature / install 里
        if (info.signature() != null && !source.confirmSignature(info, typedPhrase)) {
            message = Component.translatable("mcphone.sig.confirm_mismatch");
            return;
        }
        source.install(info,
                app -> {
                    installedRequest = true;
                    backRequest = true;
                },
                err -> message = err);
    }

}
