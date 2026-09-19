package com.november.mcphone.platform.client;

import com.mojang.blaze3d.Blaze3D;

import java.nio.file.Path;

/**
 * 交给系统自己的文件管理器打开一个目录 —— 全仓唯一碰这一句的地方。
 *
 * <h2>这一支为什么与另两支不是一份字</h2>
 *
 * 另两支走 {@code Util.getPlatform().openPath(dir)}。26.x 上这句【整个不成立了】：
 * {@code net.minecraft.Util} 整类换包到 {@code net.minecraft.util.Util}（名字没变），
 * 而 {@code Util.OS} 退化成一个只剩 {@code telemetryName()} 的空壳 ——
 * {@code openFile} 与 {@code openPath} 都不在那个枚举上了。
 *
 * <p>去处是 {@link Blaze3D#openPath(Path)}：一个静态方法，收的就是 {@link Path}，
 * 底层仍是各系统的 explorer / xdg-open / open。
 *
 * <h2>为什么这一族不能靠改名表了结</h2>
 *
 * 改名表管的是「同一个东西换了名字」，而这里是「成员换了主人、还少了一个」：
 * 把 {@code Util} 改成 {@code net.minecraft.util.Util} 之后，
 * {@code getPlatform().openFile(File)} 那几处照样编不过 —— 那种错误【长得像改名】
 * 但改名字救不了它。所以收在这儿。取证与那 10 个直接 import 的文件见
 * docs/PORTING-26.3.md §十一。
 *
 * <h2>包名里的 client 是硬要求</h2>
 *
 * dist 隔离那道闸只准路径里带 {@code /client/} 的类引用客户端与图形层的东西。
 */
public final class SystemFiles {

    private SystemFiles() {}

    /** 用系统的文件管理器打开这个目录。目录不存在时各平台自己的行为，这里不兜。 */
    public static void openInFileManager(Path dir) {
        Blaze3D.openPath(dir);
    }
}
