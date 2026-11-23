package com.november.mcphone.core.script.client;

import com.november.mcphone.core.script.pkg.AppPackage;
import com.november.mcphone.core.script.pkg.FrontendDigest;
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
 *
 * <p>{@link #frontendDigest()} 在装载时算一次存下（ADV-S2b-7）：详情页的每帧渲染与每次点击都要读它，
 * 而算一次摘要是 SHA-256 过一遍整个包（合法上限 1 MiB）。单文件形态没有包，为 null。
 */
public final class ScriptApp {
    private final ResourceLocation id;
    private final Manifest manifest;
    private final AppPackage pkg;
    private final SfcCompiler.Page entry;
    private final Map<String, SfcCompiler.Page> pages;
    private final byte[] icon;
    private final String file;
    private final String frontendDigest;

    public ScriptApp(ResourceLocation id, Manifest manifest, AppPackage pkg, SfcCompiler.Page entry, Map<String, SfcCompiler.Page> pages, byte[] icon, String file, String frontendDigest) {
        this.id = id;
        this.manifest = manifest;
        this.pkg = pkg;
        this.entry = entry;
        this.pages = pages;
        this.icon = icon;
        this.file = file;
        this.frontendDigest = frontendDigest;
    }

    public ResourceLocation id() { return id; }

    public Manifest manifest() { return manifest; }

    public AppPackage pkg() { return pkg; }

    public SfcCompiler.Page entry() { return entry; }

    public Map<String, SfcCompiler.Page> pages() { return pages; }

    public byte[] icon() { return icon; }

    public String file() { return file; }

    public String frontendDigest() { return frontendDigest; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ScriptApp)) return false;
        ScriptApp that = (ScriptApp) other;
        return java.util.Objects.equals(this.id, that.id)
                && java.util.Objects.equals(this.manifest, that.manifest)
                && java.util.Objects.equals(this.pkg, that.pkg)
                && java.util.Objects.equals(this.entry, that.entry)
                && java.util.Objects.equals(this.pages, that.pages)
                && java.util.Objects.equals(this.icon, that.icon)
                && java.util.Objects.equals(this.file, that.file)
                && java.util.Objects.equals(this.frontendDigest, that.frontendDigest);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(manifest);
        hash = 31 * hash + java.util.Objects.hashCode(pkg);
        hash = 31 * hash + java.util.Objects.hashCode(entry);
        hash = 31 * hash + java.util.Objects.hashCode(pages);
        hash = 31 * hash + java.util.Objects.hashCode(icon);
        hash = 31 * hash + java.util.Objects.hashCode(file);
        hash = 31 * hash + java.util.Objects.hashCode(frontendDigest);
        return hash;
    }

    @Override public String toString() { return "ScriptApp[id=" + id + ", manifest=" + manifest + ", pkg=" + pkg + ", entry=" + entry + ", pages=" + pages + ", icon=" + icon + ", file=" + file + ", frontendDigest=" + frontendDigest + "]"; }


    /** 装载时把摘要算好，别让渲染路径自己去算。 */
    public static ScriptApp of(ResourceLocation id, Manifest manifest, AppPackage pkg,
                               SfcCompiler.Page entry, Map<String, SfcCompiler.Page> pages,
                               byte[] icon, String file) {
        return new ScriptApp(id, manifest, pkg, entry, pages, icon, file, FrontendDigest.of(pkg));
    }

    /** {@code nav(name)} 要的那一页；空串是入口页，找不到返回 null。 */
    public SfcCompiler.Page page(String name) {
        if (name == null || name.isEmpty()) return entry;
        return pages.get(name);
    }
}
