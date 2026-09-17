package com.november.mcphone.core.script.pkg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Set;

/**
 * 作者密钥的生成与保管（施工方案 §12.6）。
 *
 * <pre>
 * config/mcphone/keys/author.key   PKCS8，48 字节
 * config/mcphone/keys/author.pub   X.509，44 字节
 * </pre>
 *
 * <h2>私钥永不上传</h2>
 *
 * 不进任何包、不进任何网络包、不进日志。这个类<b>一次都不 toString 私钥字节</b>，
 * 出错时只说文件路径。
 *
 * <h2>Windows 上没有 chmod 600</h2>
 *
 * POSIX 上设 {@code rw-------}；设不了的时候<b>不假装设上了</b> ——
 * {@link #protectedOnDisk()} 返回 false，界面上要明说"此文件未受系统级保护"。
 * 悄悄失败比没有保护更糟：玩家以为有。
 *
 * <h2>丢了就是丢了</h2>
 *
 * 不提供恢复。所以要有「导出备份」（{@link #exportBackup}），而且界面上要写明这件事。
 *
 * <h2>签名是命令式操作</h2>
 *
 * <b>不提供自动签名。</b>自动签名会让"我只是改个错别字"与"我发布了一个新版本"
 * 变成同一件事 —— 而后者是要作者自己点头的。
 */
public final class AuthorKeys {

    public static final String DIR = "config/mcphone/keys";
    public static final String PRIVATE_FILE = "author.key";
    public static final String PUBLIC_FILE = "author.pub";

    private final PrivateKey priv;
    private final PublicKey pub;
    private final boolean protectedOnDisk;

    private AuthorKeys(PrivateKey priv, PublicKey pub, boolean protectedOnDisk) {
        this.priv = priv;
        this.pub = pub;
        this.protectedOnDisk = protectedOnDisk;
    }

    /** 指纹 —— 作者的身份就是它。 */
    public String fingerprint() {
        return Signatures.fingerprint(pub);
    }

    public PublicKey publicKey() {
        return pub;
    }

    /** 公钥的 X.509 字节，写进 sig.json。 */
    public byte[] publicKeyBytes() {
        return pub.getEncoded();
    }

    /** 文件在磁盘上有没有受系统级保护。false 时界面要明说。 */
    public boolean protectedOnDisk() {
        return protectedOnDisk;
    }

    /** 签一个包摘要。<b>命令式</b>：调用方是「打包并签名」那个动作，不是自动触发。 */
    public byte[] sign(String packageDigest) {
        return Signatures.sign(priv, packageDigest);
    }

    /** 签一份轮换声明（§12.6）。用<b>旧</b>密钥签，声明里带新公钥。 */
    public byte[] signRotation(String oldFingerprint, byte[] newPubkeyX509) {
        return RotateManifest.sign(priv, oldFingerprint, newPubkeyX509);
    }

    // ---------------------------------------------------------------- 生成 / 读 / 写

    /** 有没有已经生成过。 */
    public static boolean exists(Path gameDir) {
        return Files.isRegularFile(gameDir.resolve(DIR).resolve(PRIVATE_FILE));
    }

    /** 生成一对并落盘。已经有了就抛 —— <b>不覆盖</b>：覆盖等于把作者的身份弄丢。 */
    public static AuthorKeys generate(Path gameDir) {
        Path dir = gameDir.resolve(DIR);
        if (exists(gameDir)) {
            throw PackageError.of(PackageError.Code.E_SIG_BAD_KEY,
                    "已经有一对密钥了，不覆盖 —— 覆盖等于把作者身份弄丢");
        }
        KeyPair kp = Signatures.generate();
        boolean prot;
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(PRIVATE_FILE), kp.getPrivate().getEncoded());
            Files.write(dir.resolve(PUBLIC_FILE), kp.getPublic().getEncoded());
            prot = restrict(dir.resolve(PRIVATE_FILE));
        } catch (IOException e) {
            throw PackageError.of(PackageError.Code.E_SIG_BAD_KEY, "密钥写不进去：" + e.getMessage());
        }
        return new AuthorKeys(kp.getPrivate(), kp.getPublic(), prot);
    }

    /** 读已有的。 */
    public static AuthorKeys load(Path gameDir) {
        Path dir = gameDir.resolve(DIR);
        try {
            byte[] p = Files.readAllBytes(dir.resolve(PRIVATE_FILE));
            byte[] q = Files.readAllBytes(dir.resolve(PUBLIC_FILE));
            return new AuthorKeys(Signatures.privateKey(p), Signatures.publicKey(q),
                    isRestricted(dir.resolve(PRIVATE_FILE)));
        } catch (IOException e) {
            // 只说路径，【不说内容】
            throw PackageError.of(PackageError.Code.E_SIG_BAD_KEY, "密钥读不出来：" + dir.resolve(PRIVATE_FILE));
        }
    }

    /**
     * 导出备份。<b>丢了就是丢了</b>，所以这个按钮必须有，而且界面上要写明这句话。
     *
     * <p>导出的是私钥 —— 调用方要提示玩家这份文件的敏感性，别随手丢云盘。
     */
    public static void exportBackup(Path gameDir, Path target) {
        Path src = gameDir.resolve(DIR).resolve(PRIVATE_FILE);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(src, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            restrict(target);
        } catch (IOException e) {
            throw PackageError.of(PackageError.Code.E_SIG_BAD_KEY, "备份写不出去：" + target);
        }
    }

    /** POSIX 上设成 {@code rw-------}。设不了返回 false，<b>不假装设上了</b>。 */
    private static boolean restrict(Path file) {
        try {
            Files.setPosixFilePermissions(file,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            // Windows 走到这里。真正的 ACL 要平台代码，本步只如实回答"没设上"
            return false;
        }
    }

    private static boolean isRestricted(Path file) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            return perms.size() <= 2
                    && perms.contains(PosixFilePermission.OWNER_READ);
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    /** 给「打包并签名」用：拼出 {@code META/sig.json} 的字节。 */
    public byte[] buildSigJson(String packageDigest, String author, long signedAtEpochSeconds) {
        byte[] sig = sign(packageDigest);
        String json = "{"
                + "\"format\":" + SigManifest.FORMAT + ","
                + "\"alg\":\"" + Signatures.ALG + "\","
                + "\"digest\":\"" + SigManifest.DIGEST_PREFIX + packageDigest + "\","
                + "\"pubkey\":\"" + Base64.getEncoder().encodeToString(publicKeyBytes()) + "\","
                + "\"sig\":\"" + Base64.getEncoder().encodeToString(sig) + "\","
                + "\"author\":\"" + escape(author) + "\","
                + "\"signedAt\":" + signedAtEpochSeconds
                + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : (s == null ? "" : s).toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c >= ' ') sb.append(c);
        }
        return sb.toString();
    }
}
