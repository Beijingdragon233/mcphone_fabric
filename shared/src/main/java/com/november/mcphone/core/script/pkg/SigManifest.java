package com.november.mcphone.core.script.pkg;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;

/**
 * {@code META/sig.json}（施工方案 §12.3）。
 *
 * <pre>
 * { "format":1, "alg":"ed25519", "digest":"sha256:a91c…",
 *   "pubkey":"&lt;base64 X.509 44B&gt;", "sig":"&lt;base64 64B&gt;",
 *   "author":"yumeka", "signedAt":1757203200 }
 * </pre>
 *
 * <h2>author 是装饰，不是身份</h2>
 *
 * 两个人都可以把它写成 {@code "Mojang"}。<b>身份只有公钥指纹</b>，
 * UI 上必须显示指纹，而且 author 旁边不许有任何对勾之类暗示已核实的图标。
 *
 * <h2>alg 不认识就拒，不回退成「未签名」</h2>
 *
 * 那是两档不同的状态（§12.4）：「未签名」给二次确认的路，「签名无效」硬拒绝。
 * 把一个用不认识的算法签的包显示成「未签名」，等于把硬拒绝降级成二次确认。
 */
public final class SigManifest {
    private final int format;
    private final String alg;
    private final String digest;
    private final byte[] pubkey;
    private final byte[] sig;
    private final String author;
    private final long signedAt;

    public SigManifest(int format, String alg, String digest, byte[] pubkey, byte[] sig, String author, long signedAt) {
        this.format = format;
        this.alg = alg;
        this.digest = digest;
        this.pubkey = pubkey;
        this.sig = sig;
        this.author = author;
        this.signedAt = signedAt;
    }

    public int format() { return format; }

    public String alg() { return alg; }

    public String digest() { return digest; }

    public byte[] pubkey() { return pubkey; }

    public byte[] sig() { return sig; }

    public String author() { return author; }

    public long signedAt() { return signedAt; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SigManifest)) return false;
        SigManifest that = (SigManifest) other;
        return this.format == that.format
                && java.util.Objects.equals(this.alg, that.alg)
                && java.util.Objects.equals(this.digest, that.digest)
                && java.util.Objects.equals(this.pubkey, that.pubkey)
                && java.util.Objects.equals(this.sig, that.sig)
                && java.util.Objects.equals(this.author, that.author)
                && this.signedAt == that.signedAt;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(format);
        hash = 31 * hash + java.util.Objects.hashCode(alg);
        hash = 31 * hash + java.util.Objects.hashCode(digest);
        hash = 31 * hash + java.util.Objects.hashCode(pubkey);
        hash = 31 * hash + java.util.Objects.hashCode(sig);
        hash = 31 * hash + java.util.Objects.hashCode(author);
        hash = 31 * hash + java.util.Objects.hashCode(signedAt);
        return hash;
    }

    @Override public String toString() { return "SigManifest[format=" + format + ", alg=" + alg + ", digest=" + digest + ", pubkey=" + pubkey + ", sig=" + sig + ", author=" + author + ", signedAt=" + signedAt + "]"; }


    public static final int FORMAT = 1;

    /** {@code digest} 字段的前缀。 */
    public static final String DIGEST_PREFIX = "sha256:";

    /** 作者显示名的长度上限。它只是装饰，不该占满半个界面。 */
    public static final int MAX_AUTHOR = 32;

    public static SigManifest parse(byte[] json) {
        JsonObject root = object(json);

        int format = intField(root, "format");
        if (format != FORMAT) throw PackageError.of(PackageError.Code.E_SIG_BAD_FORMAT, format);

        String alg = stringField(root, "alg");
        if (!Signatures.knownAlg(alg)) throw PackageError.of(PackageError.Code.E_SIG_UNKNOWN_ALG, alg);

        String digest = stringField(root, "digest");
        byte[] pubkey = base64Field(root, "pubkey");
        byte[] sig = base64Field(root, "sig");
        String author = root.has("author") ? stringField(root, "author") : "";
        if (author.length() > MAX_AUTHOR) author = author.substring(0, MAX_AUTHOR);
        long signedAt = root.has("signedAt") ? root.get("signedAt").getAsLong() : 0;

        return new SigManifest(format, alg, digest, pubkey, sig, author, signedAt);
    }

    /** 作者指纹。<b>这才是身份。</b> */
    public String fingerprint() {
        return Signatures.fingerprint(pubkey);
    }

    /** 签名里写的摘要与包实际算出来的一不一样。 */
    public boolean digestMatches(String packageDigest) {
        String want = digest.startsWith(DIGEST_PREFIX) ? digest.substring(DIGEST_PREFIX.length()) : digest;
        return want.equals(packageDigest);
    }

    /** 验签。摘要对不上或者验不过都返回 false —— 两者都落在「签名无效」那一档。 */
    public boolean verify(String packageDigest) {
        if (!digestMatches(packageDigest)) return false;
        PublicKey key;
        try {
            key = Signatures.publicKey(pubkey);
        } catch (PackageError e) {
            return false;
        }
        return Signatures.verify(key, packageDigest, sig);
    }

    // ---------------------------------------------------------------- 解析辅助

    static JsonObject object(byte[] json) {
        try {
            com.google.gson.JsonElement parsed = JsonParser.parseString(new String(json, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw PackageError.of(PackageError.Code.E_SIG_BAD_JSON, "顶层不是对象");
            return parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw PackageError.of(PackageError.Code.E_SIG_BAD_JSON, String.valueOf(e.getMessage()));
        }
    }

    static String stringField(JsonObject o, String name) {
        if (!o.has(name) || !o.get(name).isJsonPrimitive()) {
            throw PackageError.of(PackageError.Code.E_SIG_MISSING_FIELD, name);
        }
        return o.get(name).getAsString();
    }

    static int intField(JsonObject o, String name) {
        if (!o.has(name) || !o.get(name).isJsonPrimitive()) {
            throw PackageError.of(PackageError.Code.E_SIG_MISSING_FIELD, name);
        }
        return o.get(name).getAsInt();
    }

    static byte[] base64Field(JsonObject o, String name) {
        String v = stringField(o, name);
        try {
            return Base64.getDecoder().decode(v);
        } catch (IllegalArgumentException e) {
            throw PackageError.of(PackageError.Code.E_SIG_BAD_BASE64, name);
        }
    }
}
