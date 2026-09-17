package com.november.mcphone.core.script.pkg;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 签名文案与确认交互（施工方案 §12.4、§12.5）。
 *
 * <p><b>这里测不了的</b>：那段话在屏幕上真的显示出来、「已签名」处没有暗示内容安全的图标 ——
 * 那要 runClient 才能看。这里钉住的是<b>文案本身</b>与<b>放行判据</b>。
 *
 * <p>跑法：{@code ./gradlew assertTests}（在 {@code platforms/<目标名>/} 下）。
 */
public class SignCopyTest {

    static int checks = 0;
    static final List<String> failures = new ArrayList<>();

    static void eq(Object actual, Object expected, String what) {
        checks++;
        if (!java.util.Objects.equals(actual, expected)) failures.add(what + "  期望 " + expected + "，实际 " + actual);
    }

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) failures.add(what);
    }

    static JsonObject lang(String file) {
        try (InputStream in = SignCopyTest.class.getResourceAsStream("/assets/mcphone/lang/" + file)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    /** §12.5 的原文，逐字。<b>改这里之前先回去读 §12.1</b>。 */
    static final String[][] EXPECTED_ZH = {
            {SigCopy.SIG_INVALID, "签名无效 —— 这个包在传播过程中可能被改动过。不建议安装。"},
            {SigCopy.SIG_KEY_CHANGED, "作者密钥变了。上次是 %s，这次是 %s。可能是作者换了设备，也可能不是同一个人。"},
            {SigCopy.SIG_UNSIGNED, "未签名。无法确认是谁做的、有没有被改过。"},
            {SigCopy.SIG_UNKNOWN, "签名有效。作者指纹 %s —— 你还没装过这个作者的 App。"},
            {SigCopy.SIG_TRUSTED, "来自 %s（%s）"},
    };

    /** 逐字比对。§12.5 的措辞是设计的一部分，不是随手写的说明文字。 */
    static void copyVerbatim() {
        JsonObject zh = lang("zh_cn.json");
        if (zh == null) {
            // 资源不在类路径上时不红：那是打包方式的问题，不是文案错了
            System.out.println("（lang 资源不在类路径上，跳过逐字比对）");
            return;
        }
        for (String[] pair : EXPECTED_ZH) {
            check(zh.has(pair[0]), "lang 里要有 " + pair[0]);
            if (zh.has(pair[0])) eq(zh.get(pair[0]).getAsString(), pair[1], pair[0] + " 要与 §12.5 逐字一致");
        }
        check(zh.has(SigCopy.INSTALL_NOTE), "INSTALL_NOTE 要在 lang 里");
        String note = zh.has(SigCopy.INSTALL_NOTE) ? zh.get(SigCopy.INSTALL_NOTE).getAsString() : "";
        check(note.contains("不能读写你的文件"), "INSTALL_NOTE 要说清 App 碰不到你的文件");
        check(note.contains("不能执行程序"), "INSTALL_NOTE 要说清 App 不能执行程序");
        check(note.contains("谁做的"), "INSTALL_NOTE 要说签名确认的是「谁做的」");
        check(note.contains("不是"), "INSTALL_NOTE 要把「不是内容安不安全」那一半讲出来");
    }

    /**
     * 文案里不许出现「安全」。
     *
     * <p>§12.1 的区分是这套设计的前提：签名确认的是「谁做的、有没有被改过」。
     * 一旦有一条文案写成"已签名 = 安全"，玩家就会把绿色当成无害 —— 而挡毒的是包内容白名单与沙箱。
     *
     * <p>{@code INSTALL_NOTE} 是例外：它正是用来说"不是内容安不安全"的。
     */
    static void noSafetyClaim() {
        JsonObject zh = lang("zh_cn.json");
        if (zh == null) return;
        for (String[] pair : EXPECTED_ZH) {
            if (!zh.has(pair[0])) continue;
            String v = zh.get(pair[0]).getAsString();
            check(!v.contains("安全"), pair[0] + " 里不许出现「安全」，实际：" + v);
        }
        JsonObject en = lang("en_us.json");
        if (en != null) {
            for (String[] pair : EXPECTED_ZH) {
                if (!en.has(pair[0])) continue;
                String v = en.get(pair[0]).getAsString().toLowerCase(java.util.Locale.ROOT);
                check(!v.contains("safe") && !v.contains("secure"),
                        pair[0] + " 的英文里也不许出现 safe/secure，实际：" + v);
            }
        }
    }

    /** 四档 → 文案的映射。UI 查这张表，不自己判。 */
    static void stateToCopy() {
        eq(SigCopy.keyFor(TrustState.State.INVALID), SigCopy.SIG_INVALID, "无效");
        eq(SigCopy.keyFor(TrustState.State.KEY_CHANGED), SigCopy.SIG_KEY_CHANGED, "密钥变了");
        eq(SigCopy.keyFor(TrustState.State.UNSIGNED), SigCopy.SIG_UNSIGNED, "未签名");
        eq(SigCopy.keyFor(TrustState.State.UNKNOWN_AUTHOR), SigCopy.SIG_UNKNOWN, "陌生作者");
        eq(SigCopy.keyFor(TrustState.State.TRUSTED), SigCopy.SIG_TRUSTED, "已信任");

        // 五档都映射得出来，一个不落
        for (TrustState.State s : TrustState.State.values()) {
            check(SigCopy.keyFor(s) != null && !SigCopy.keyFor(s).isEmpty(), s + " 要有文案");
        }
    }

    /** §12.4：「作者密钥变了」要输入确认短语，<b>不是点一下</b>。 */
    static void confirmPhrase() {
        String newFp = "080e-6088-5258-2466";
        String oldFp = "ad88-d3ca-61de-73ee";
        var changed = new TrustState.Verdict(TrustState.State.KEY_CHANGED, newFp, oldFp, "yumeka");

        check(!SigCopy.canProceed(changed, null), "什么都不输不放行");
        check(!SigCopy.canProceed(changed, ""), "空串不放行");
        check(!SigCopy.canProceed(changed, "确认"), "打两个字不放行 —— 那就是「点一下」");
        check(!SigCopy.canProceed(changed, oldFp), "抄成旧指纹不放行");
        check(!SigCopy.canProceed(changed, "080e60885258 2466"), "省掉连字符不放行 —— 分组是指纹的一部分");
        check(SigCopy.canProceed(changed, newFp), "抄对新指纹才放行");
        check(SigCopy.canProceed(changed, "  " + newFp.toUpperCase(java.util.Locale.ROOT) + " "),
                "首尾空白与大小写不该成为拦路虎");

        eq(SigCopy.requiredPhrase(newFp), newFp, "要输入的就是新指纹本身 —— 逼玩家真的去看一眼");

        // 其余档位不需要短语
        for (TrustState.State s : TrustState.State.values()) {
            if (s == TrustState.State.KEY_CHANGED) continue;
            var v = new TrustState.Verdict(s, "x", null, "");
            eq(SigCopy.canProceed(v, null), s.installable, s + " 不需要短语，能否继续只看 installable");
        }

        // 硬拒绝：输什么都进不去
        var invalid = new TrustState.Verdict(TrustState.State.INVALID, "x", null, "");
        check(!SigCopy.canProceed(invalid, "x"), "签名无效时输什么都不放行");
        check(!SigCopy.canProceed(invalid, null), "同上");
    }

    public static void main(String[] args) {
        copyVerbatim();
        noSafetyClaim();
        stateToCopy();
        confirmPhrase();

        System.out.println("断言 " + checks + " 条");
        if (!failures.isEmpty()) {
            System.out.println("失败 " + failures.size() + " 条：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过");
    }
}
