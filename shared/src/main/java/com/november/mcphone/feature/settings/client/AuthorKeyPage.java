package com.november.mcphone.feature.settings.client;

import com.november.mcphone.core.client.PhoneTheme;
import com.november.mcphone.core.script.pkg.AuthorKeys;
import com.november.mcphone.core.script.pkg.PackageError;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/**
 * 「设置 → 开发者 → 我的签名密钥」（施工方案 §12.6）。
 *
 * <h2>这一页必须说清的三件事</h2>
 *
 * <ol>
 *   <li><b>指纹才是身份</b>。页面上显示的是它，不是作者名 —— 两个人都能自称同一个名字</li>
 *   <li><b>丢了就是丢了</b>：没有找回、没有提示问题、服主也重置不了。所以要有「导出备份」</li>
 *   <li>私钥文件<b>在这台机器上有没有受系统级保护</b>。设不上就明说，不假装设上了</li>
 * </ol>
 *
 * <h2>页面上不显示私钥</h2>
 *
 * 一个字节都不显示。要备份就导出成文件，而不是让玩家去截图一串 base64。
 *
 * <h2>签名是命令式的</h2>
 *
 * 这一页有「打包并签名」，但<b>没有"改了就自动签"</b>（§12.6）——
 * 自动签名会让"我只是改个错别字"与"我发布了一个新版本"变成同一件事。
 */
public final class AuthorKeyPage {

    private static final int PAD = 6;
    private static final int ROW = 12;

    private String fingerprint;
    private Boolean protectedOnDisk;
    private Component notice;

    private int genY, backupY, btnX, btnW;

    public void open() {
        notice = null;
        refresh();
    }

    public void close() {
        notice = null;
    }

    private Path gameDir() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.gameDirectory == null ? Path.of(".") : mc.gameDirectory.toPath();
    }

    private void refresh() {
        Path game = gameDir();
        if (!AuthorKeys.exists(game)) {
            fingerprint = null;
            protectedOnDisk = null;
            return;
        }
        try {
            AuthorKeys k = AuthorKeys.load(game);
            fingerprint = k.fingerprint();
            protectedOnDisk = k.protectedOnDisk();
        } catch (PackageError e) {
            fingerprint = null;
            protectedOnDisk = null;
            notice = Component.literal(e.getMessage());
        }
    }

    public void render(GuiGraphics g, int phoneLeft, int phoneTop,
                       int screenW, int screenH, int statusH, int navH,
                       int mouseX, int mouseY, Font font) {
        int x = phoneLeft + PAD;
        int y = phoneTop + statusH + PAD;
        int w = screenW - PAD * 2;
        btnX = x;
        btnW = w;

        g.drawString(font, Component.translatable("mcphone.settings.author_key").getString(),
                x, y, PhoneTheme.FONT_COLOR_STATUS, false);
        y += ROW + 2;

        if (fingerprint == null) {
            for (var l : font.split(Component.translatable("mcphone.sig.key_none"), w)) {
                g.drawString(font, l, x, y, PhoneTheme.FONT_COLOR_NAV, false);
                y += font.lineHeight;
            }
            y += 4;
            genY = y;
            button(g, font, x, y, w, Component.translatable("mcphone.sig.key_generate").getString(),
                    mouseX, mouseY);
            y += ROW + 6;
            backupY = -1;
        } else {
            // 指纹就是身份 —— 作者把它贴在自己的页面上，玩家一眼对得上
            g.drawString(font, Component.translatable("mcphone.sig.key_fingerprint").getString(),
                    x, y, PhoneTheme.FONT_COLOR_NAV, false);
            y += ROW;
            g.drawString(font, fingerprint, x, y, PhoneTheme.FONT_COLOR_STATUS, false);
            y += ROW + 4;

            // 权限：设不上就明说，不假装设上了
            if (Boolean.FALSE.equals(protectedOnDisk)) {
                for (var l : font.split(Component.translatable("mcphone.sig.key_unprotected"), w)) {
                    g.drawString(font, l, x, y, PhoneTheme.FONT_COLOR_CHAT_SEND, false);
                    y += font.lineHeight;
                }
                y += 3;
            }

            backupY = y;
            button(g, font, x, y, w, Component.translatable("mcphone.sig.key_export").getString(),
                    mouseX, mouseY);
            y += ROW + 6;
            genY = -1;
        }

        // 丢了就是丢了 —— 这句话必须在页面上，不能只写在文档里
        for (var l : font.split(Component.translatable("mcphone.sig.key_lost"), w)) {
            g.drawString(font, l, x, y, PhoneTheme.FONT_COLOR_NAV, false);
            y += font.lineHeight;
        }

        if (notice != null) {
            y += 4;
            for (var l : font.split(notice, w)) {
                g.drawString(font, l, x, y, PhoneTheme.FONT_COLOR_CHAT_SEND, false);
                y += font.lineHeight;
            }
        }
    }

    private void button(GuiGraphics g, Font font, int x, int y, int w, String label, int mx, int my) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + ROW;
        g.fill(x, y, x + w, y + ROW, hover ? PhoneTheme.COLOR_BUTTON_HOVER : PhoneTheme.COLOR_BUTTON);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 2,
                PhoneTheme.FONT_COLOR_BUTTON, false);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (mx < btnX || mx > btnX + btnW) return false;
        try {
            if (genY > 0 && my >= genY && my <= genY + ROW) {
                AuthorKeys k = AuthorKeys.generate(gameDir());
                fingerprint = k.fingerprint();
                protectedOnDisk = k.protectedOnDisk();
                notice = Component.translatable("mcphone.sig.key_generated");
                return true;
            }
            if (backupY > 0 && my >= backupY && my <= backupY + ROW) {
                Path out = gameDir().resolve("mcphone-author-key-backup.key");
                AuthorKeys.exportBackup(gameDir(), out);
                // 只说路径，【不说内容】
                notice = Component.translatable("mcphone.sig.key_exported", out.getFileName().toString());
                return true;
            }
        } catch (PackageError e) {
            notice = Component.literal(e.getMessage());
            return true;
        }
        return false;
    }
}
