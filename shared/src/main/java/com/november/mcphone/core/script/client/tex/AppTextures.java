package com.november.mcphone.core.script.client.tex;

import com.mojang.blaze3d.platform.NativeImage;
import com.november.mcphone.MCphone;
import com.november.mcphone.core.client.ImageCodec;
import com.november.mcphone.core.script.layout.ImageSizes;
import com.november.mcphone.core.script.pkg.AppPackage;
import com.november.mcphone.core.script.pkg.PackageError;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 包内图片对应的贴图（施工方案 §5.2 image、§5.4、§28.2 #13）。
 *
 * <p>三道上限：单张 ≤ {@link #MAX_BYTES}、边长 ≤ {@link #MAX_SIDE}、每个 App ≤ {@link #MAX_PER_APP} 张。
 * 超限与坏文件一律【拒绝加载】返回 null，由 §8.4 的占位分支画占位图 —— 不抛，不许一张坏图把商店带崩。
 *
 * <p><b>判定只做一次</b>：{@link #size} 与 {@link #of} 走同一张表。第 17 张在哪一边被拒，
 * 另一边就一定也拒 —— 否则 measure 按原始尺寸留了位置，render 却画占位图，两边对不上。
 * 谁是第 17 张按【第一次被问到的先后】定，与画的顺序无关。
 *
 * <p><b>尺寸与上传分两步</b>：measure 只要宽高（读 PNG 头，不碰显存），画的时候才真的上传。
 * 一页里三十张图只露出两张时，另外二十八张一个字节的显存都不占。
 *
 * <p><b>线程</b>：全在渲染线程。{@link #of} 会上传贴图，别从别处调。
 */
public final class AppTextures {

    /** 单张字节上限（§11.2 / §28.2 #13）。 */
    public static final int MAX_BYTES = 64 * 1024;
    /** 边长上限，宽高各自算（§5.2）。 */
    public static final int MAX_SIDE = 128;
    /** 每个 App 能装多少张（§28.2 #13）。 */
    public static final int MAX_PER_APP = 16;
    /** 素材只认这个目录下的（§11.2）。 */
    static final String ASSETS = "assets/";

    private AppTextures() {
    }

    /** 一条素材的判定结果。测试按它断言，比"返回了 null"说得清楚。 */
    enum Result {
        OK,
        /** 包里没有这条路径 */
        MISSING,
        /** 不在 assets/ 下，或者后缀不是 .png */
        BAD_PATH,
        /** 字节数超 {@link #MAX_BYTES} */
        TOO_LARGE,
        /** 宽或高超 {@link #MAX_SIDE} */
        TOO_BIG,
        /** 不是 PNG：假扩展名，或者头就坏了 */
        NOT_PNG,
        /** 头没问题，像素解不开或者传不上去 */
        BROKEN,
        /** 这个 App 已经有 {@link #MAX_PER_APP} 张了 */
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
        /** src → 判定结果；插入序，第 17 张按这个序算。 */
        final Map<String, Entry> entries = new LinkedHashMap<>();
        int accepted;
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
     * <p>{@link com.november.mcphone.core.script.layout.LayoutEngine#layout} 建完树就按树序问一遍，
     * 之后整趟排版用那一份答案 —— 名额也在那一趟里定下来，与玩家滚到哪儿无关。
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
        if (e.texture == null) {
            e.texture = uploader.upload(pkg.entry(src));
            if (e.texture == null) {
                // 头过了像素没过：判定就地改成 BROKEN，否则每一帧都要重解一遍这张坏图
                e.result = Result.BROKEN;
                warn(pkg, src, Result.BROKEN);
                return null;
            }
        }
        return e.texture.location();
    }

    /**
     * 还回这个包占的显存。关页面、卸包时调 —— 不调的话贴图会一直挂在 TextureManager 上，
     * 开关两百次就是两百份（§8.8）。
     */
    public static void release(AppPackage pkg) {
        if (pkg == null) return;
        App app = APPS.remove(pkg.digest());
        if (app == null) return;
        for (Entry e : app.entries.values()) {
            uploader.release(e.texture);
        }
    }

    /**
     * 全部还回去并让 {@link #epoch()} 前进。挂在客户端资源重载上（与 PhoneSkin.clearCache 同一处）。
     *
     * <p>重载之后原来那批 DynamicTexture 未必还在；与其赌它在不在，不如全丢掉重来 ——
     * 一个包至多 16 张 64 KiB 的图，重传的代价是一帧里几毫秒，而赌输的代价是整页白图。
     */
    public static void clearCache() {
        for (App app : APPS.values()) {
            for (Entry e : app.entries.values()) {
                uploader.release(e.texture);
            }
        }
        APPS.clear();
        epoch++;
    }

    /** 这条素材的判定结果，只给测试用。和 {@link #width} 一样会当场判一次，也一样占名额。 */
    static Result resultOf(AppPackage pkg, String src) {
        Entry e = entry(pkg, src);
        return e == null ? null : e.result;
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
        e.result = judge(app, pkg, src, e);
        app.entries.put(src, e);
        if (e.result == Result.OK) {
            app.accepted++;
        } else {
            warn(pkg, src, e.result);
        }
        return e;
    }

    /** 判一条素材能不能用。宽高在这里填进 {@code out}。 */
    private static Result judge(App app, AppPackage pkg, String src, Entry out) {
        // 后缀走 S1 那份判据：它按 ASCII 小写取，所以 .PNG 与 .png 是同一条路
        if (!src.startsWith(ASSETS) || !"png".equals(PackageError.PathRules.extensionOf(src))) {
            return Result.BAD_PATH;
        }
        byte[] png = pkg.entry(src);
        if (png == null) return Result.MISSING;
        if (png.length > MAX_BYTES) return Result.TOO_LARGE;

        int[] size = PngHeader.size(png);
        if (size == null) return Result.NOT_PNG;
        if (size[0] > MAX_SIDE || size[1] > MAX_SIDE) return Result.TOO_BIG;

        // 名额在这里扣，不在上传时扣：measure 与 render 问的是同一张表，
        // 扣在上传时的话，没画到的那张在 measure 里算数、在 render 里不算数
        if (app.accepted >= MAX_PER_APP) return Result.TOO_MANY;

        out.width = size[0];
        out.height = size[1];
        return Result.OK;
    }

    private static void warn(AppPackage pkg, String src, Result why) {
        MCphone.LOGGER.warn("[MCphone] App {} 的图片 {} 用不了：{}", pkg.manifest().id(), src, reason(why));
    }

    private static String reason(Result why) {
        return switch (why) {
            case MISSING -> "包里没有这个文件";
            case BAD_PATH -> "素材只能放在 " + ASSETS + " 下，且只能是 .png";
            case TOO_LARGE -> "超过 " + (MAX_BYTES / 1024) + " KiB";
            case TOO_BIG -> "超过 " + MAX_SIDE + "×" + MAX_SIDE;
            case NOT_PNG -> "不是 PNG";
            case BROKEN -> "PNG 头没问题，像素解不开";
            case TOO_MANY -> "这个 App 的图片已经有 " + MAX_PER_APP + " 张了";
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
