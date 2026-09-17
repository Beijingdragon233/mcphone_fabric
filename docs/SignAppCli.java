package com.november.mcphone.core.script.pkg;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 「打包并签名」的离线那一条路（施工方案 §12.6）：{@code ./gradlew signApp -PappDir=...}。
 *
 * <p><b>与游戏里那个按钮调的是同一份实现</b>（{@link AuthorKeys#buildSigJson}）——
 * 两条路各写一份的话，迟早有一条签出来的包另一条验不过。
 *
 * <p>住在 {@code docs/} 下：那一层由每个目标编译但<b>不进模组 jar</b>，
 * 而这是个开发期工具，没必要发给玩家。
 *
 * <h2>签名是命令式的</h2>
 *
 * 没有"检测到改动就自动签"这种东西（§12.6）。自动签名会让"我只是改个错别字"
 * 与"我发布了一个新版本"变成同一件事，而后者是要作者自己点头的。
 *
 * <h2>失败要说清是哪一步</h2>
 *
 * 读包 / 算摘要 / 读私钥 / 写 sig.json —— 四步各自报各自的，
 * 不然作者只知道"签失败了"，得自己猜是密钥没生成还是包有问题。
 */
public final class SignAppCli {

    private SignAppCli() {
    }

    /**
     * @param args {@code <appDir> <gameDir> [author] [outZip]}
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("用法: signApp <appDir> <gameDir> [author] [outZip]");
            System.exit(2);
            return;
        }
        Path appDir = Path.of(args[0]);
        Path gameDir = Path.of(args[1]);
        String author = args.length > 2 ? args[2] : "";
        Path out = args.length > 3 ? Path.of(args[3])
                : appDir.resolveSibling(appDir.getFileName() + "-signed.zip");

        // ---- 第一步：读包
        Map<String, byte[]> entries;
        try {
            entries = readDir(appDir);
        } catch (Exception e) {
            fail("读包", appDir + "：" + e.getMessage());
            return;
        }
        if (entries.isEmpty()) {
            fail("读包", appDir + " 下一个文件都没有");
            return;
        }
        if (!entries.containsKey("manifest.json")) {
            fail("读包", "包根缺 manifest.json —— zip 形态的包必须有它（§11.2）");
            return;
        }
        System.out.println("[signApp] 读到 " + entries.size() + " 个文件");

        // ---- 第二步：算摘要
        String digest;
        try {
            digest = PackageDigest.of(entries);
        } catch (Exception e) {
            fail("算摘要", String.valueOf(e.getMessage()));
            return;
        }
        System.out.println("[signApp] 摘要 " + digest);

        // ---- 第三步：读私钥
        AuthorKeys keys;
        try {
            if (!AuthorKeys.exists(gameDir)) {
                fail("读私钥", "还没有作者密钥。先在游戏里「设置 → 开发者 → 我的签名密钥」生成一对，"
                        + "或者把备份放回 " + gameDir.resolve(AuthorKeys.DIR).resolve(AuthorKeys.PRIVATE_FILE));
                return;
            }
            keys = AuthorKeys.load(gameDir);
        } catch (Exception e) {
            fail("读私钥", String.valueOf(e.getMessage()));
            return;
        }
        System.out.println("[signApp] 作者指纹 " + keys.fingerprint());
        if (!keys.protectedOnDisk()) {
            System.out.println("[signApp] ⚠ 私钥文件未受系统级保护 —— 这台机器上设不了「只有你能读」");
        }

        // ---- 第四步：写 sig.json 并出包
        try {
            byte[] sigJson = keys.buildSigJson(digest, author, System.currentTimeMillis() / 1000);
            Map<String, byte[]> all = new LinkedHashMap<>(entries);
            all.put(PackageReader.SIG, sigJson);
            Files.createDirectories(out.toAbsolutePath().getParent());
            Files.write(out, zip(all));
        } catch (Exception e) {
            fail("写 sig.json", String.valueOf(e.getMessage()));
            return;
        }
        System.out.println("[signApp] 签好了：" + out.toAbsolutePath());
        System.out.println("[signApp] 提醒：签名确认的是「谁做的、有没有被改过」，不是「内容安不安全」。");
    }

    private static void fail(String step, String detail) {
        System.err.println("[signApp] 失败于「" + step + "」：" + detail);
        System.exit(1);
    }

    /** 目录 → 条目表。路径用 {@code /} 分隔、相对包根，与 zip 里的写法一致。 */
    static Map<String, byte[]> readDir(Path dir) throws Exception {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (var walk = Files.walk(dir)) {
            List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
            for (Path f : files) {
                String rel = dir.relativize(f).toString().replace('\\', '/');
                // META/ 下的东西不进摘要，也不该由作者手放 —— 签名这一步自己写
                if (rel.startsWith("META/")) continue;
                out.put(rel, Files.readAllBytes(f));
            }
        }
        return out;
    }

    static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    static String utf8(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
