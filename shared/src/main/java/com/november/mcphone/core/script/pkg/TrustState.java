package com.november.mcphone.core.script.pkg;

/**
 * 四档信任状态的判定（施工方案 §12.4）。<b>纯函数：输入包与信任库，输出状态。UI 只渲染，不判断。</b>
 *
 * <p>UI 里再判一遍就会有两份判据，而它们迟早对不上 —— 那种不一致的表现是
 * "界面说可以装，装下去被拒"，或者更糟，反过来。
 */
public final class TrustState {

    private TrustState() {
    }

    /**
     * 五种结果。
     *
     * <p><b>只有 {@link #INVALID} 是硬拒绝。</b>把「未签名」也做成硬拒绝会逼所有人去找绕过办法，
     * 而未签名的包并不比签名的更危险（§12.1：挡毒的是白名单与沙箱，不是签名）。
     */
    public enum State {
        /** 验签失败 / 摘要对不上 / alg 不认识。<b>拒绝安装，不给"仍然继续"</b>。 */
        INVALID("mcphone.sig.invalid", false),
        /** 同 appId 装过，但指纹与记录的不同。要<b>输入确认短语</b>，不是点一下。 */
        KEY_CHANGED("mcphone.sig.key_changed", true),
        /** 没有 META/sig.json。二次确认，指纹位置写「无」。 */
        UNSIGNED("mcphone.sig.unsigned", true),
        /** 验签通过，指纹首次见到。二次确认，记录指纹。 */
        UNKNOWN_AUTHOR("mcphone.sig.unknown", true),
        /** 验签通过，指纹在信任库里且 trusted。直接安装。 */
        TRUSTED("mcphone.sig.trusted", true);

        /** 文案的本地化键（§12.5）。<b>不是文本</b>。 */
        public final String messageKey;

        /** 能不能往下走。只有 INVALID 是 false。 */
        public final boolean installable;

        State(String messageKey, boolean installable) {
            this.messageKey = messageKey;
            this.installable = installable;
        }

        /** 要不要输入确认短语（不是点一下）。 */
        public boolean needsPhrase() {
            return this == KEY_CHANGED;
        }

        /** 要不要二次确认。 */
        public boolean needsConfirm() {
            return this == UNSIGNED || this == UNKNOWN_AUTHOR;
        }
    }

    /**
     * 判定结果。
     *
     * @param state           五档之一
     * @param fingerprint     这个包的作者指纹；未签名时是 null（UI 上写「无」）
     * @param knownFingerprint 这个 appId 上次见到的指纹，只有 {@link State#KEY_CHANGED} 时非 null
     * @param author          包里自称的作者名。<b>装饰，不是身份</b>
     */
    public record Verdict(State state, String fingerprint, String knownFingerprint, String author) {
    }

    /**
     * 判一次。
     *
     * @param pkg     已经读出来的包
     * @param appId   它的 id，用来查"这个 App 上次是谁签的"
     * @param trust   信任库
     */
    public static Verdict of(AppPackage pkg, String appId, TrustStore trust) {
        byte[] sigJson = pkg.signature();

        if (sigJson == null) {
            // 没签名。指纹位置写「无」，二次确认（§12.4）
            return new Verdict(State.UNSIGNED, null, null, "");
        }

        SigManifest sig;
        try {
            sig = SigManifest.parse(sigJson);
        } catch (PackageError e) {
            // alg 不认识、JSON 坏了、base64 坏了 —— 全是「签名无效」，
            // 【不回退成「未签名」】：那会把硬拒绝降级成二次确认
            return new Verdict(State.INVALID, null, null, "");
        }

        if (!sig.verify(pkg.digest())) {
            return new Verdict(State.INVALID, sig.fingerprint(), null, sig.author());
        }

        String fp = sig.fingerprint();

        // 作者被封了：这一条压在所有"验签通过"的判定之前（§12.7）
        if (trust.blocked(fp)) {
            return new Verdict(State.INVALID, fp, null, sig.author());
        }

        String known = trust.fingerprintFor(appId);
        if (known != null && !known.equals(fp)) {
            return new Verdict(State.KEY_CHANGED, fp, known, sig.author());
        }

        if (trust.trusted(fp)) {
            return new Verdict(State.TRUSTED, fp, null, trust.displayName(fp));
        }
        return new Verdict(State.UNKNOWN_AUTHOR, fp, null, sig.author());
    }
}
