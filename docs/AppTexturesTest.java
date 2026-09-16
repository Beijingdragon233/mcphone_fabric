package com.november.mcphone.core.script.client.tex;

import com.november.mcphone.core.client.ImageCodec;
import com.november.mcphone.core.script.layout.ImageSizes;
import com.november.mcphone.core.script.pkg.AppPackage;
import com.november.mcphone.core.script.pkg.PackageReader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 包内图片的判定、名额、缓存与释放（施工方案 §5.2 image、§5.4、§28.2 #13、§8.8）。
 *
 * <p>贴图真的进没进显存这里测不了 —— 没有 Minecraft 的运行环境。所以上传口换成假的：
 * 要测的是"哪些图收、哪些图拒、拒了之后还回不回来"，那部分一个 GL 调用都不需要。
 * 五个缩放下画出来对不对、F3+T 之后刷不刷新，只能在游戏里看（§8.8）。
 *
 * <pre>
 * javac -d /tmp/t $(find shared/src/main/java -name '*.java') docs/AppTexturesTest.java
 * </pre>
 */
public class AppTexturesTest {

    static int checks = 0;
    static final List<String> failures = new ArrayList<>();

    static void eq(Object actual, Object expected, String what) {
        checks++;
        if (!java.util.Objects.equals(actual, expected)) {
            failures.add(what + "  期望 " + expected + "，实际 " + actual);
        }
    }

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) failures.add(what);
    }

    public static void main(String[] args) throws Exception {
        pngHeader();
        paths();
        limits();
        quota();
        cacheAndRelease();
        reload();
        bothSides();

        System.out.println("断言 " + checks + " 条");
        if (!failures.isEmpty()) {
            System.out.println("失败 " + failures.size() + " 条：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }

    // ============================================================
    //  PNG 头
    // ============================================================

    static void pngHeader() {
        eq(Arrays.toString(PngHeader.size(png(64, 48))), "[64, 48]", "头里读得出宽高");
        eq(Arrays.toString(PngHeader.size(png(1, 1))), "[1, 1]", "1×1 也读得出");
        eq(PngHeader.size(null), null, "null 不是 PNG");
        eq(PngHeader.size(new byte[0]), null, "空字节不是 PNG");
        eq(PngHeader.size(Arrays.copyOf(png(64, 48), 23)), null, "少一个字节就不够判");

        // 改后缀名的别的格式：签名对不上，解码之前就拦住
        byte[] zipBytes = new byte[64];
        zipBytes[0] = 'P';
        zipBytes[1] = 'K';
        eq(PngHeader.size(zipBytes), null, "PK 开头的 zip 改名成 .png 也不收");

        byte[] jpeg = new byte[64];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        eq(PngHeader.size(jpeg), null, "JPEG 改名成 .png 也不收");

        byte[] sigOnly = png(64, 48);
        sigOnly[3] = 'X';
        eq(PngHeader.size(sigOnly), null, "签名第 4 个字节错了就不收");

        byte[] notIhdr = png(64, 48);
        notIhdr[12] = 'I';
        notIhdr[13] = 'D';
        notIhdr[14] = 'A';
        notIhdr[15] = 'T';
        eq(PngHeader.size(notIhdr), null, "签名对但第一个块不是 IHDR，不收");

        eq(PngHeader.size(png(0, 48)), null, "宽 0 不收");
        eq(PngHeader.size(png(64, 0)), null, "高 0 不收");

        // PNG 的宽高是 31 位无符号；最高位置 1 的写法不合法，按有符号读正好是负数
        byte[] huge = png(64, 48);
        huge[16] = (byte) 0x80;
        eq(PngHeader.size(huge), null, "宽的最高位置 1 不收");

        byte[] big = png(20000, 20000);
        eq(Arrays.toString(PngHeader.size(big)), "[20000, 20000]",
           "天文数字照读 —— 拦它是上限那一关的事，头这一层只负责读得准");
    }

    // ============================================================
    //  路径规则（§11.2：只认 assets/ 下的 .png）
    // ============================================================

    static void paths() throws Exception {
        Fake fake = reset();
        AppPackage pkg = pkg(map(
                "assets/a.png", png(16, 16),
                "assets/sub/b.png", png(8, 8),
                "assets/c.PNG", png(4, 4),
                "assets/d.txt", b("不是图"),
                "root.png", png(16, 16)));

        eq(AppTextures.resultOf(pkg, "assets/a.png"), AppTextures.Result.OK, "assets/ 下的 .png 收");
        eq(AppTextures.resultOf(pkg, "assets/sub/b.png"), AppTextures.Result.OK, "子目录照收");
        eq(AppTextures.resultOf(pkg, "assets/c.PNG"), AppTextures.Result.OK,
           "后缀按小写比，.PNG 与 .png 同一条路（S1 的 extensionOf 就是这么判的）");
        eq(AppTextures.resultOf(pkg, "assets/d.txt"), AppTextures.Result.BAD_PATH, "后缀不是 png 就拒");
        eq(AppTextures.resultOf(pkg, "root.png"), AppTextures.Result.BAD_PATH, "不在 assets/ 下就拒");
        eq(AppTextures.resultOf(pkg, "assets/没有这张.png"), AppTextures.Result.MISSING, "包里没有就拒");
        eq(AppTextures.resultOf(pkg, "assets/.png"), AppTextures.Result.BAD_PATH, "只有后缀没有名字，拒");
        eq(AppTextures.resultOf(pkg, ""), AppTextures.Result.BAD_PATH, "空路径拒");
        eq(AppTextures.resultOf(pkg, "assets/a.png.txt"), AppTextures.Result.BAD_PATH, "双后缀按最后一段判");

        eq(AppTextures.size(null, "assets/a.png"), null, "没有包时没有尺寸，不抛");
        eq(AppTextures.size(pkg, null), null, "src 是 null 时没有尺寸，不抛");
        eq(AppTextures.of(null, null), null, "两个都是 null 也只是拿不到贴图");
        eq(fake.uploads, 0, "判路径一张都没上传");
    }

    // ============================================================
    //  三道上限
    // ============================================================

    static void limits() throws Exception {
        Fake fake = reset();
        AppPackage pkg = pkg(map(
                "assets/ok.png", png(128, 128, 64 * 1024),
                "assets/wide.png", png(129, 128),
                "assets/tall.png", png(128, 129),
                "assets/fat.png", png(16, 16, 64 * 1024 + 1),
                "assets/fake.png", b("PK 这其实是个 zip，只是改了后缀而已，长度要够 24 个字节"),
                "assets/short.png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}));

        eq(AppTextures.resultOf(pkg, "assets/ok.png"), AppTextures.Result.OK, "128×128、整 64 KiB 正好收");
        eq(wh(pkg, "assets/ok.png"), "[128, 128]", "收下的图给得出原始尺寸");

        eq(AppTextures.resultOf(pkg, "assets/wide.png"), AppTextures.Result.TOO_BIG, "129 宽超边长");
        eq(AppTextures.resultOf(pkg, "assets/tall.png"), AppTextures.Result.TOO_BIG, "129 高超边长");
        eq(AppTextures.resultOf(pkg, "assets/fat.png"), AppTextures.Result.TOO_LARGE, "64 KiB 多一个字节就拒");
        eq(AppTextures.resultOf(pkg, "assets/fake.png"), AppTextures.Result.NOT_PNG, "假扩展名拒");
        eq(AppTextures.resultOf(pkg, "assets/short.png"), AppTextures.Result.NOT_PNG, "截断的 PNG 拒");

        for (String bad : List.of("assets/wide.png", "assets/tall.png", "assets/fat.png",
                                  "assets/fake.png", "assets/short.png")) {
            eq(AppTextures.size(pkg, bad), null, bad + " 拒了就没有原始尺寸");
            eq(AppTextures.of(pkg, bad), null, bad + " 拒了就没有贴图");
            eq(AppTextures.sizes(pkg).size(bad), null, bad + " 在布局那一侧也没有");
        }
        eq(fake.uploads, 0, "被拒的一张都没送去上传");

        eq(AppTextures.MAX_BYTES, 64 * 1024, "单张上限 64 KiB（§11.2）");
        eq(AppTextures.MAX_SIDE, 128, "边长上限 128（§5.2）");
        eq(AppTextures.MAX_PER_APP, 16, "每个 App 16 张（§28.2 #13）");

        // 头过了、像素没过：判定就地改成 BROKEN，不是每帧重解一次
        Fake broken = reset();
        broken.fail = true;
        AppPackage p2 = pkg(map("assets/ok.png", png(16, 16)));
        eq(AppTextures.resultOf(p2, "assets/ok.png"), AppTextures.Result.OK, "光看头是好的");
        eq(AppTextures.of(p2, "assets/ok.png"), null, "像素解不开就拿不到贴图");
        eq(AppTextures.resultOf(p2, "assets/ok.png"), AppTextures.Result.BROKEN, "判定改成 BROKEN");
        eq(AppTextures.of(p2, "assets/ok.png"), null, "再问还是没有");
        eq(broken.uploads, 1, "坏图只解一次，不是每帧一次");
        eq(AppTextures.size(p2, "assets/ok.png"), null,
           "BROKEN 之后尺寸也不给了 —— 布局按占位尺寸排，而不是按一张画不出来的图的尺寸");
    }

    // ============================================================
    //  每个 App 16 张
    // ============================================================

    static void quota() throws Exception {
        Fake fake = reset();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) entries.put("assets/n" + i + ".png", png(8, 8));
        AppPackage pkg = pkg(entries);

        for (int i = 0; i < 16; i++) {
            eq(AppTextures.resultOf(pkg, "assets/n" + i + ".png"), AppTextures.Result.OK, "第 " + (i + 1) + " 张收");
        }
        eq(AppTextures.resultOf(pkg, "assets/n16.png"), AppTextures.Result.TOO_MANY, "第 17 张拒");
        eq(AppTextures.resultOf(pkg, "assets/n17.png"), AppTextures.Result.TOO_MANY, "第 18 张照拒");
        eq(AppTextures.size(pkg, "assets/n16.png"), null, "第 17 张没有尺寸");
        eq(AppTextures.of(pkg, "assets/n16.png"), null, "第 17 张没有贴图");
        eq(AppTextures.resultOf(pkg, "assets/n0.png"), AppTextures.Result.OK, "先来的那张不受影响");

        // 名额按"第一次被问到"的先后算，与画的顺序无关：问过一次就定了，不会被后来的挤掉
        eq(AppTextures.resultOf(pkg, "assets/n15.png"), AppTextures.Result.OK, "第 16 张还在");

        // 被拒的不占名额
        Fake f2 = reset();
        Map<String, byte[]> mixed = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) mixed.put("assets/bad" + i + ".png", png(200, 200));
        for (int i = 0; i < 16; i++) mixed.put("assets/good" + i + ".png", png(8, 8));
        AppPackage p2 = pkg(mixed);
        for (int i = 0; i < 5; i++) {
            eq(AppTextures.resultOf(p2, "assets/bad" + i + ".png"), AppTextures.Result.TOO_BIG, "超边长的先被拒");
        }
        for (int i = 0; i < 16; i++) {
            eq(AppTextures.resultOf(p2, "assets/good" + i + ".png"), AppTextures.Result.OK,
               "被拒的不占名额，16 张好图一张不少");
        }
        eq(f2.uploads, 0, "名额这一节没画过任何东西");

        // 两个 App 各算各的
        Fake f3 = reset();
        Map<String, byte[]> sixteen = new LinkedHashMap<>();
        for (int i = 0; i < 16; i++) sixteen.put("assets/n" + i + ".png", png(8, 8));
        AppPackage a = pkg(sixteen);
        Map<String, byte[]> other = new LinkedHashMap<>(sixteen);
        other.put("assets/extra.png", png(8, 8));
        AppPackage bpkg = pkg(other);
        for (int i = 0; i < 16; i++) AppTextures.resultOf(a, "assets/n" + i + ".png");
        eq(AppTextures.resultOf(a, "assets/n0.png"), AppTextures.Result.OK, "A 装满 16 张");
        eq(AppTextures.resultOf(bpkg, "assets/extra.png"), AppTextures.Result.OK, "B 的名额是自己的");
        eq(f3.uploads, 0, "还是没画");
    }

    // ============================================================
    //  缓存与释放（§8.8：装卸 200 次不涨）
    // ============================================================

    static void cacheAndRelease() throws Exception {
        Fake fake = reset();
        AppPackage pkg = pkg(map("assets/a.png", png(16, 16), "assets/b.png", png(32, 8)));

        AppTextures.of(pkg, "assets/a.png");
        AppTextures.of(pkg, "assets/a.png");
        AppTextures.of(pkg, "assets/a.png");
        eq(fake.uploads, 1, "同一张图只上传一次");
        eq(fake.live, 1, "显存里一张");

        AppTextures.size(pkg, "assets/b.png");
        eq(fake.uploads, 1, "只问尺寸不上传 —— 一页里没露出来的图不占显存");
        AppTextures.of(pkg, "assets/b.png");
        eq(fake.uploads, 2, "画到了才上传");
        eq(fake.live, 2, "显存里两张");

        AppTextures.release(pkg);
        eq(fake.live, 0, "关页面还回去了");
        eq(fake.releases, 2, "两张都还了");
        eq(AppTextures.cachedEntries(), 0, "判定表也清了");

        AppTextures.release(pkg);
        eq(fake.releases, 2, "再释放一次不重复还账");
        AppTextures.release(null);
        eq(fake.live, 0, "释放 null 不抛");

        // 装卸 200 次：显存与表都不许涨
        Fake cycle = reset();
        AppPackage p = pkg(map("assets/a.png", png(16, 16), "assets/b.png", png(8, 8),
                               "assets/bad.png", png(200, 200)));
        for (int i = 0; i < 200; i++) {
            AppTextures.of(p, "assets/a.png");
            AppTextures.of(p, "assets/b.png");
            AppTextures.of(p, "assets/bad.png");   // 拒掉的那张也不许攒
            check(cycle.live <= 2, "第 " + i + " 轮里显存里至多两张");
            AppTextures.release(p);
            eq(cycle.live, 0, "第 " + i + " 轮关掉之后显存是空的");
            eq(AppTextures.cachedEntries(), 0, "第 " + i + " 轮关掉之后判定表是空的");
        }
        eq(cycle.uploads, 400, "两百轮各传两张，没有多出来的");
        eq(cycle.releases, 400, "传了多少还了多少");
    }

    // ============================================================
    //  资源重载
    // ============================================================

    static void reload() throws Exception {
        Fake fake = reset();
        AppPackage pkg = pkg(map("assets/a.png", png(16, 16)));

        int before = AppTextures.epoch();
        AppTextures.of(pkg, "assets/a.png");
        eq(AppTextures.epoch(), before, "只是画图，epoch 不动");

        AppTextures.clearCache();
        eq(AppTextures.epoch(), before + 1, "重载之后 epoch 前进一格（§7.6 据此重排）");
        eq(fake.live, 0, "重载把显存还回去了");
        eq(AppTextures.cachedEntries(), 0, "判定表也清了");

        AppTextures.of(pkg, "assets/a.png");
        eq(fake.uploads, 2, "重载之后重新上传一份");
        eq(wh(pkg, "assets/a.png"), "[16, 16]", "尺寸重新读得出来");

        int now = AppTextures.epoch();
        AppTextures.clearCache();
        AppTextures.clearCache();
        eq(AppTextures.epoch(), now + 2, "连着重载两次就是两格");
    }

    // ============================================================
    //  measure 与 render 问的是同一张表
    // ============================================================

    static void bothSides() throws Exception {
        Fake fake = reset();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < 17; i++) entries.put("assets/n" + i + ".png", png(24, 12));
        AppPackage pkg = pkg(entries);

        // 先让 render 那一侧问满 16 张，measure 再问第 17 张：两边必须都说不行
        for (int i = 0; i < 16; i++) AppTextures.of(pkg, "assets/n" + i + ".png");
        eq(AppTextures.size(pkg, "assets/n16.png"), null, "render 用光了名额，measure 这边也没有");
        eq(AppTextures.sizes(pkg).size("assets/n16.png"), null, "绑给布局的那份也没有");
        eq(AppTextures.of(pkg, "assets/n16.png"), null, "render 再问也没有");

        // 反过来：measure 先问满，render 那一侧同样拿不到
        Fake f2 = reset();
        AppPackage p2 = pkg(entries);
        ImageSizes bound = AppTextures.sizes(p2);
        for (int i = 0; i < 16; i++) {
            eq(Arrays.toString(bound.size("assets/n" + i + ".png")), "[24, 12]",
               "布局那一侧读得出原始尺寸");
        }
        eq(AppTextures.of(p2, "assets/n16.png"), null, "measure 用光了名额，render 这边也没有");
        eq(bound.size("assets/n16.png"), null, "两边都拒的是同一张");
        eq(f2.uploads, 0, "第 17 张没送去上传");
    }

    // ============================================================
    //  小工具
    // ============================================================

    /** 假上传口：数上传与归还，不碰显存。 */
    static final class Fake implements AppTextures.Uploader {
        int uploads;
        int releases;
        int live;
        /** true 时模拟"头是好的、像素解不开" */
        boolean fail;

        @Override
        public ImageCodec.Texture upload(byte[] png) {
            uploads++;
            if (fail) return null;
            live++;
            // location 给 null：这里没有 Minecraft 的运行环境，而这一节要测的不是像素
            return new ImageCodec.Texture(null, 1, 1);
        }

        @Override
        public void release(ImageCodec.Texture texture) {
            if (texture == null) return;
            releases++;
            live--;
        }
    }

    /** 换上一个干净的假上传口，顺带把上一节留下的表清空。 */
    static Fake reset() {
        Fake fake = new Fake();
        AppTextures.uploader(fake);
        return fake;
    }

    /** 原始尺寸的可读形式，拿不到时是 "null"。 */
    static String wh(AppPackage pkg, String src) {
        return Arrays.toString(AppTextures.size(pkg, src));
    }

    static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** 只有头是真的 PNG：判定看的就是这 24 个字节，像素在这套测试里没人解。 */
    static byte[] png(int w, int h) {
        return png(w, h, 64);
    }

    static byte[] png(int w, int h, int totalBytes) {
        byte[] out = new byte[Math.max(24, totalBytes)];
        // 头之后填不可压缩的噪声：全是 0 的话 zip 压出 600:1，S1 的压缩比闸（100:1）先把包拒了
        long seed = 0x5DEECE66DL;
        for (int i = 24; i < out.length; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            out[i] = (byte) (seed >>> 33);
        }
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        System.arraycopy(sig, 0, out, 0, sig.length);
        put32(out, 8, 13);
        out[12] = 'I';
        out[13] = 'H';
        out[14] = 'D';
        out[15] = 'R';
        put32(out, 16, w);
        put32(out, 20, h);
        return out;
    }

    static void put32(byte[] out, int off, int v) {
        out[off] = (byte) (v >>> 24);
        out[off + 1] = (byte) (v >>> 16);
        out[off + 2] = (byte) (v >>> 8);
        out[off + 3] = (byte) v;
    }

    static Map<String, byte[]> map(Object... kv) {
        Map<String, byte[]> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (byte[]) kv[i + 1]);
        return m;
    }

    /** 走真的 PackageReader 造一个包：判定读的是 AppPackage.entry，别的路子造不出同样的东西。 */
    static AppPackage pkg(Map<String, byte[]> assets) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", b(MANIFEST));
        entries.put("ui.json", b("{\"type\":\"column\"}"));
        entries.put("ui.mss", b(".x { color: #fff; }"));
        entries.put("icon.png", png(16, 16));
        entries.putAll(assets);
        return PackageReader.read(zip(entries));
    }

    static final String MANIFEST = "{"
            + "\"format\":1,"
            + "\"id\":\"example:tex\","
            + "\"version\":\"1.0.0\","
            + "\"name\":\"贴图\","
            + "\"author\":\"yumeka\","
            + "\"description\":\"测贴图用的包\","
            + "\"icon\":\"icon.png\","
            + "\"ui\":{\"tree\":\"ui.json\",\"style\":\"ui.mss\"},"
            + "\"engine\":\"declarative-1\"}";

    static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
