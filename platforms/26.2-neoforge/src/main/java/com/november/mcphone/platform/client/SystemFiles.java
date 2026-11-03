package com.november.mcphone.platform.client;

import net.minecraft.util.Util;

import java.nio.file.Path;

/**
 * 交给系统自己的文件管理器打开一个目录 —— 全仓唯一碰这一句的地方。
 *
 * <p>26.2 的 {@code Util.getPlatform()} 仍保留 {@code openPath} 与 {@code openUri}。
 * 26.2 才移除这两个成员并把调用挪到 {@code Blaze3D}；因此本支沿用旧入口。
 *
 * <h2>包名里的 client 是硬要求</h2>
 *
 * dist 隔离那道闸只准路径里带 {@code /client/} 的类引用客户端与图形层的东西。
 */
public final class SystemFiles {

    private SystemFiles() {}

    /** 用系统的文件管理器打开这个目录。目录不存在时各平台自己的行为，这里不兜。 */
    public static void openInFileManager(Path dir) {
        Util.getPlatform().openPath(dir);
    }

    public static void openUri(String uri) {
        Util.getPlatform().openUri(uri);
    }
}
