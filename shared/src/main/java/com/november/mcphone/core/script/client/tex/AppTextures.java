package com.november.mcphone.core.script.client.tex;

import com.mojang.blaze3d.platform.NativeImage;
import com.november.mcphone.MCphone;
import com.november.mcphone.core.client.ImageCodec;
import com.november.mcphone.core.script.layout.ImageSizes;
import com.november.mcphone.core.script.pkg.AppPackage;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 包内图片对应的贴图（施工方案 §5.2 image、§5.4、§28.2 #13）。
 *
 * <p>两道判定上限：单张 ≤ {@link #MAX_BYTES}、边长 ≤ {@link #MAX_SIDE}。超限、坏文件、不在包里的
 * 一律【拒绝加载】返回 null，由 §8.4 的占位分支画占位图 —— 不抛，不许一张坏图把商店带崩。
 *
 * <p><b>尺寸与上传分两步</b>：measure 只要宽高（读 PNG 头，不碰显存），画的时候才真的上传。
 * 一页里三十张图只露出两张时，另外二十八张一个字节的显存都不占。宽高不限量 —— 一条 8 个字节，
 * 限它没有意义；限的是显存。
 *
 * <p><b>每个 App 同时至多 {@link #MAX_PER_APP} 张贴图在显存里</b>（§28.2 #13），满了淘汰最久没画的那张，
 * 与聊天图片那套（ChatImageCache）同一个办法。§28.2 #13 的处置写的是"拒绝加载"，这里没照做：
 * 按那个写法，先被问到的 16 张会【永久】占住名额 —— 换个 tab 再回来，后来的那几张在关页面之前
 * 一直是占位图，而显存里躺着的是玩家早就不看的图。淘汰制把这道闸变回它本来的意思：一道内存上限。
 * 代价是一页同时露出超过 16 张图会每帧换进换出 —— 那已经超出 §28.2 #13 的预算，作者该减图。
 *
 * <p><b>没有调用方 = 泄漏</b>：{@link #release} 现在全仓没人调（宿主页是 S9/S10 的事）。
 * 接宿主页的人必须在关页面、卸包时调它，否则开关两百次就是两百份贴图挂在 TextureManager 上（§8.8）。
 *
 * <p><b>线程</b>：全在渲染线程。{@link #of} 第一次被问到时就地解码再上传 —— 一张 128×128 是一万六千个
 * 像素，一页十六张压在开页面那一帧里是几毫秒。要更平滑就得像聊天图片那样搬去后台线程，P0 不做。
 */
public final class AppTextures {

    /** 单张字节上限（§11.2 / §28.2 #13）。 */
    public static final int MAX_BYTES = 64 * 1024;
    /** 边长上限，宽高各自算（§5.2）。 */
    public static final int MAX_SIDE = 128;
    /** 每个 App 同时能有多少张贴图在显存里（§28.2 #13）。满了淘汰最久没画的那张。 */
    public static final int MAX_PER_APP = 16;
    /**
     * 一个 App 最多留多少条判定。
     *
     * <p>模板里的 {@code :src} 可以是表达式，每次重排都能造出一批没见过的路径；判定表要是不封顶，
     * 它就随重排次数一直涨，一条最长 512 字节（S1 的路径上限）。
     */
    static final int MAX_JUDGED = MAX_PER_APP * 4;
    /** 一个 App 最多报多少条"这张图用不了"。同一个坏 src 每次重排都报一遍的话，日志就没法看了。 */
    static final int MAX_WARNINGS = 32;

    private AppTextures() {
    }

    /** 一条素材的判定结果。测试按它断言，比"返回了 null"说得清楚。 */
    enum Result {
        OK,
        /** 包里没有这条路径 */
        MISSING,
        /** 后缀不是 .png */
        BAD_PATH,
        /** 字节数超 {@link #MAX_BYTES} */
        TOO_LARGE,
        /** 宽或高超 {@link #MAX_SIDE} */
        TOO_BIG,
        /** 不是 PNG：假扩展名，或者头就坏了 */
        NOT_PNG,
        /** 头没问题，像素解不开或者传不上去 */
        BROKEN,
        /** 这个 App 问过的图片路径太多了（多半是 {@code :src} 绑了个每次都变的表达式），判定表封顶了 */
        TOO_MANY
    }

    private static final class Entry {
        Result result;
        int width;
        int height;
        /** 画过才有。测试里的假上传口给的 location 是 null。 */
        ImageCodec.Texture texture;
    }

    private static final class App {
        /** src → 判定结果。插入序，封顶在 {@link #MAX_JUDGED}。 */
        final Map<String, Entry> entries = new LinkedHashMap<>();
        /** 显存里那几张，访问序：满了淘汰迭代器给出的第一条，也就是最久没画的那张。 */
        final Map<String, Entry> live = new LinkedHashMap<>(16, 0.75f, true);
        int warned;
    }

    /** key 是包摘要：同一个包换个 AppPackage 实例装进来，贴图不必重传。 */
    private static final Map<String, App> APPS = new HashMap<>();

    private static int epoch;

    /**
     * §7.6 的四个 epoch 之一。资源重载之后这个数会变，页面据此重排 ——
     * 贴图全丢了，原始尺寸要重新读，布局跟着变。
     */
    public static int epoch() {
        return epoch;
    }

    /** 这张图的原始尺寸 {宽, 高}；用不了返回 null（§5.2：image 不写 w / h 时按原始尺寸算）。 */
    public static int[] size(AppPackage pkg, String src) {
        Entry e = entry(pkg, src);
        return e == null || e.result != Result.OK ? null : new int[]{e.width, e.height};
    }

    /**
     * 把这个包绑给布局（§7.4 的 measure 要原始尺寸）。
     *
     * <p>{@link com.november.mcphone.core.script.layout.LayoutEngine#layout} 建完树就问一遍，
     * 之后整趟排版用那一份答案。问尺寸不占显存，也不限量 —— 一页里三十张图，三十个宽高都给得出来，
     * 显存里同时只会有 {@link #MAX_PER_APP} 张。
     */
    public static ImageSizes sizes(AppPackage pkg) {
        return src -> size(pkg, src);
    }

    /**
     * 可以画的贴图，拿不到返回 null（占位图的事交给 §8.4 的调用方）。
     *
     * <p>第一次问到某张图时才上传，之后走缓存。必须在渲染线程调。
     */
    public static ResourceLocation of(AppPackage pkg, String src) {
        Entry e = entry(pkg, src);
        if (e == null || e.result != Result.OK) return null;
        App app = APPS.get(pkg.digest());
        if (e.texture != null) {
            app.live.get(src);              // 访问序：刷一下，淘汰的时候它就不是最老的那个
            return e.texture.location();
        }
        trim(app);
        e.texture = uploader.upload(pkg.entry(src));
        if (e.texture == null) {
            // 头过了像素没过：判定就地改成 BROKEN，否则每一帧都要重解一遍这张坏图。
            // epoch 跟着前进：measure 是按头里那个宽高留的位置，现在这张图没了，得按占位尺寸重排一次
            e.result = Result.BROKEN;
            epoch++;
            warn(pkg, src, Result.BROKEN);
            return null;
        }
        app.live.put(src, e);
        return e.texture.location();
    }

    /** 腾出一个位置：显存里满了就把最久没画的那张还回去。它下次被画到时会重传。 */
    private static void trim(App app) {
        var it = app.live.entrySet().iterator();
        while (app.live.size() >= MAX_PER_APP && it.hasNext()) {
            Entry eldest = it.next().getValue();
            uploader.release(eldest.texture);
            eldest.texture = null;
            it.remove();
        }
    }

    /**
     * 还回这个包占的显存。关页面、卸包时调 —— 不调的话贴图会一直挂在 TextureManager 上，
     * 开关两百次就是两百份（§8.8）。
     *
     * <p>没有引用计数：表按包摘要分组，而摘要相同就意味着内容逐字节相同，两个 AppPackage 实例
     * 共用一份贴图。同一个包同时被两处打开时，先关的那一方把另一方的也还了 —— 另一方下一帧重传，
     * 表现是闪一下。眼下不会发生（一次只开一页），真要同时开两页就得在这儿加计数。
     */
    public static void release(AppPackage pkg) {
        if (pkg == null) return;
        App app = APPS.remove(pkg.digest());
        if (app == null) return;
        for (Entry e : app.live.values()) {
            uploader.release(e.texture);
            e.texture = null;
        }
        app.live.clear();
    }

    /**
     * 全部还回去并让 {@link #epoch()} 前进。挂在客户端资源重载上（与 PhoneSkin.clearCache 同一处）。
     *
     * <p>重载之后原来那批 DynamicTexture 未必还在；与其赌它在不在，不如全丢掉重来 ——
     * 一个包至多 16 张 64 KiB 的图，重传的代价是一帧里几毫秒，而赌输的代价是整页白图。
     */
    public static void clearCache() {
        for (App app : APPS.values()) {
            for (Entry e : app.live.values()) uploader.release(e.texture);
        }
        APPS.clear();
        epoch++;
    }

    /** 这条素材的判定结果，只给测试用。和 {@link #width} 一样会当场判一次，也一样占名额。 */
    static Result resultOf(AppPackage pkg, String src) {
        Entry e = entry(pkg, src);
        return e == null ? null : e.result;
    }

    /** 这张图的贴图在不在显存里，只给测试用：假上传口给的 location 是 null，{@link #of} 的返回值分不出来。 */
    static boolean uploaded(AppPackage pkg, String src) {
        App app = pkg == null ? null : APPS.get(pkg.digest());
        Entry e = app == null ? null : app.entries.get(src);
        return e != null && e.texture != null;
    }

    /** 表里攒了多少条判定，只给测试用：装卸两百次之后它必须回到装之前的数（§8.8）。 */
    static int cachedEntries() {
        int n = 0;
        for (App app : APPS.values()) n += app.entries.size();
        return n;
    }

    /** 查表，没有就当场判一次。判定结果（含拒绝）一律留下：每帧重判一次坏图等于每帧解一次码。 */
    private static Entry entry(AppPackage pkg, String src) {
        if (pkg == null || src == null) return null;
        App app = APPS.computeIfAbsent(pkg.digest(), k -> new App());
        Entry known = app.entries.get(src);
        if (known != null) return known;

        Entry e = new Entry();
        if (app.entries.size() >= MAX_JUDGED) {
            // 这条不进表：表满了还往里塞，:src 绑一个每次都变的表达式就能让它一直涨
            e.result = Result.TOO_MANY;
            warn(app, pkg, src, e.result);
            return e;
        }
        e.result = judge(pkg, src, e);
        app.entries.put(src, e);
        if (e.result != Result.OK) warn(app, pkg, src, e.result);
        return e;
    }

    /**
     * 判一条素材能不能用。宽高在这里填进 {@code out}。
     *
     * <p>判据只有"是不是 .png"与"在不在包里"两条形状上的 —— §11.2 还写了素材只能放在 {@code assets/} 下，
     * 那条这里【不查】：查 src 形状的地方是 S2 的 {@code NodeParser.imageSrc} 与 S7 的 {@code PropRules}，
     * 它们只要求 {@code .png}。在这儿单独多一条，等于同一件事两处判据，装得进、校验全过的包到画的时候整批变占位图。
     * 要收紧就收紧在那两处（作者当场拿到错误码），或者装包时按 §11.2 拒——不在这儿。
     */
    private static Result judge(AppPackage pkg, String src, Entry out) {
        // 后缀与 NodeParser.imageSrc 逐字一致，不走 PathRules.extensionOf 的小写化：
        // 那边收不了 .PNG，这边收了也没用，反倒是两处判据说了两件事
        if (!src.endsWith(".png")) return Result.BAD_PATH;
        byte[] png = pkg.entry(src);
        if (png == null) return Result.MISSING;

        // 先判格式再判字节数：反过来的话，一个 100 KiB 的假 PNG 报的是"超过 64 KiB"，把人往错的方向指
        int[] size = PngHeader.size(png);
        if (size == null) return Result.NOT_PNG;
        if (png.length > MAX_BYTES) return Result.TOO_LARGE;
        if (size[0] > MAX_SIDE || size[1] > MAX_SIDE) return Result.TOO_BIG;

        out.width = size[0];
        out.height = size[1];
        return Result.OK;
    }

    private static void warn(App app, AppPackage pkg, String src, Result why) {
        if (app.warned >= MAX_WARNINGS) return;
        app.warned++;
        MCphone.LOGGER.warn("[MCphone] App {} 的图片 {} 用不了：{}", pkg.manifest().id(), src, reason(why));
        if (app.warned == MAX_WARNINGS) {
            MCphone.LOGGER.warn("[MCphone] App {} 用不了的图片已经报了 {} 条，后面的不再报",
                    pkg.manifest().id(), MAX_WARNINGS);
        }
    }

    private static void warn(AppPackage pkg, String src, Result why) {
        App app = APPS.get(pkg.digest());
        if (app != null) warn(app, pkg, src, why);
    }

    private static String reason(Result why) {
        return switch (why) {
            case MISSING -> "包里没有这个文件";
            case BAD_PATH -> "只能是 .png";
            case TOO_LARGE -> "超过 " + (MAX_BYTES / 1024) + " KiB";
            case TOO_BIG -> "超过 " + MAX_SIDE + "×" + MAX_SIDE;
            case NOT_PNG -> "不是 PNG";
            case BROKEN -> "PNG 头没问题，像素解不开";
            case TOO_MANY -> "这个 App 问过的图片路径已经有 " + MAX_JUDGED + " 条了";
            case OK -> "";
        };
    }

    // ============================================================
    //  上传口
    // ============================================================

    /**
     * 贴图怎么进显存。游戏里走 {@link ImageCodec}；docs 的断言测试换一个假的 ——
     * 那里没有 Minecraft 的运行环境，而要测的是判定、名额与释放，不是像素。
     */
    interface Uploader {
        /** 上传一张已经过判定的 PNG，传不上返回 null。 */
        ImageCodec.Texture upload(byte[] png);

        /** 还回去。传 null 是合法的（这张图还没画过）。 */
        void release(ImageCodec.Texture texture);
    }

    private static Uploader uploader = new GameUploader();

    /** 换上传口，只给测试用。换的时候表里不能留着别人的贴图。 */
    static void uploader(Uploader replacement) {
        clearCache();
        uploader = replacement == null ? new GameUploader() : replacement;
    }

    private static final class GameUploader implements Uploader {
        @Override
        public ImageCodec.Texture upload(byte[] png) {
            // MAX_SIDE 在这儿是"不缩放"的保证：判定已经挡掉了更大的，缩放路径走不到
            NativeImage image = ImageCodec.decodeAndScale(png, MAX_SIDE);
            return image == null ? null : ImageCodec.upload(image, "script_app_");
        }

        @Override
        public void release(ImageCodec.Texture texture) {
            ImageCodec.release(texture);
        }
    }
}
