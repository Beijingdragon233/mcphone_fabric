package com.november.mcphone.core.script.client;

import com.november.mcphone.core.script.pkg.AppPackage;
import com.november.mcphone.core.script.pkg.Manifest;
import com.november.mcphone.core.script.sfc.SfcCompiler;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * 一个装好的脚本 App（施工方案 §14.1）：清单 + 各页的编译产物 + 包。
 *
 * <p><b>编译一次，常驻</b>（§9.9）。这个对象从 {@link ScriptAppFolder} 出来之后就不再变，重开页面只做实例化。
 *
 * <p>两种形态（§11.2）在这里收敛成一个东西：
 * <ul>
 *   <li>单个 {@code .vue}：清单是文件里的 {@code <manifest>} 块，{@link #pkg()} 为 null ——
 *       没有包也就没有素材，{@code image} 一律画占位图。图标是清单里 data URI 解出来的字节。</li>
 *   <li>zip：清单是 {@code manifest.json}，入口是 {@code app.vue}，{@code pages/*.vue} 是别的页，
 *       素材在 {@code assets/} 下，图标是包里那张 png。</li>
 * </ul>
 */
public record ScriptApp(
        ResourceLocation id,
        Manifest manifest,
        /** zip 形态才有；单文件形态是 null。image 与图标都要看它。 */
        AppPackage pkg,
        /** 入口那一页，{@code nav()} 回到它用空串。 */
        SfcCompiler.Page entry,
        /** {@code nav('detail')} → {@code pages/detail.vue}。不含入口页。 */
        Map<String, SfcCompiler.Page> pages,
        /** 图标的原始 PNG 字节，没有就是 null。 */
        byte[] icon,
        /** 从哪个文件读来的，只用于日志与重装。 */
        String file) {

    /** {@code nav(name)} 要的那一页；空串是入口页，找不到返回 null。 */
    public SfcCompiler.Page page(String name) {
        if (name == null || name.isEmpty()) return entry;
        return pages.get(name);
    }
}
