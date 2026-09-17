package com.november.mcphone.core.script.pkg;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TOFU 信任库（施工方案 §12.3），照 SSH 主机密钥那个模型：<b>首次见到即记录</b>。
 *
 * <pre>config/mcphone/authors.json
 * 指纹 → { 显示名, 首次见到时间, 装过的 App 列表, trusted, blocked }</pre>
 *
 * <p>{@code blocked} 是 §12.7 的吊销：标了之后该作者所有包一律拒绝安装。
 * 服务端那一侧的白名单/黑名单是另一层（{@code mcphone-server.toml} 的 {@code [authors]}），
 * 两层各管各的 —— 服务端管不了玩家本地装的纯前端 App，那本来也没有特权。
 */
public final class TrustStore {

    /** 一个作者的记录里最多记几个 appId。只是给人看的，不必无限长。 */
    public static final int MAX_APPS_PER_AUTHOR = 64;

    /** 一条记录。 */
    public record Author(String fingerprint, String displayName, long firstSeen,
                         List<String> apps, boolean trusted, boolean blocked, String pubkeyBase64) {
    }

    private final Map<String, Author> byFingerprint = new LinkedHashMap<>();

    /** appId → 上次装的时候是谁签的。判「作者密钥变了」要它。 */
    private final Map<String, String> appAuthor = new LinkedHashMap<>();

    public boolean trusted(String fingerprint) {
        Author a = byFingerprint.get(fingerprint);
        return a != null && a.trusted() && !a.blocked();
    }

    public boolean blocked(String fingerprint) {
        Author a = byFingerprint.get(fingerprint);
        return a != null && a.blocked();
    }

    public String displayName(String fingerprint) {
        Author a = byFingerprint.get(fingerprint);
        return a == null ? "" : a.displayName();
    }

    /** 这个 App 上次是谁签的，没装过返回 null。 */
    public String fingerprintFor(String appId) {
        return appAuthor.get(appId);
    }

    public Author get(String fingerprint) {
        return byFingerprint.get(fingerprint);
    }

    /** 首次见到就记一条（§12.3 的 TOFU）。已经有了就只更新显示名与时间。 */
    public void record(String fingerprint, String displayName, byte[] pubkeyX509, long now) {
        Author old = byFingerprint.get(fingerprint);
        if (old == null) {
            byFingerprint.put(fingerprint, new Author(fingerprint, displayName, now,
                    List.of(), false, false, java.util.Base64.getEncoder().encodeToString(pubkeyX509)));
        } else {
            byFingerprint.put(fingerprint, new Author(fingerprint, displayName, old.firstSeen(),
                    old.apps(), old.trusted(), old.blocked(), old.pubkeyBase64()));
        }
    }

    /** 玩家确认安装之后调：把这个 App 记到这个作者名下，并把作者标成已信任。 */
    public void trust(String fingerprint, String appId) {
        Author a = byFingerprint.get(fingerprint);
        if (a == null) return;
        List<String> apps = new ArrayList<>(a.apps());
        if (!apps.contains(appId)) {
            apps.add(appId);
            while (apps.size() > MAX_APPS_PER_AUTHOR) apps.remove(0);
        }
        byFingerprint.put(fingerprint, new Author(a.fingerprint(), a.displayName(), a.firstSeen(),
                List.copyOf(apps), true, a.blocked(), a.pubkeyBase64()));
        appAuthor.put(appId, fingerprint);
    }

    /** 封禁 / 解封（§12.7）。 */
    public void setBlocked(String fingerprint, boolean blocked) {
        Author a = byFingerprint.get(fingerprint);
        if (a == null) return;
        byFingerprint.put(fingerprint, new Author(a.fingerprint(), a.displayName(), a.firstSeen(),
                a.apps(), a.trusted(), blocked, a.pubkeyBase64()));
    }

    /**
     * 处理一份轮换声明（§12.6）。
     *
     * <p><b>旧公钥从信任库里取，不是从包里取</b> —— 从包里取等于让声明自己证明自己。
     *
     * @return 接受了就返回新指纹，拒绝返回 null
     */
    public String applyRotation(RotateManifest rotate, long now) {
        Author old = byFingerprint.get(rotate.oldFingerprint());
        if (old == null || !old.trusted() || old.blocked()) return null;     // 旧的没被信任，不接受
        byte[] oldPub = java.util.Base64.getDecoder().decode(old.pubkeyBase64());
        if (!rotate.verify(oldPub)) return null;                              // 旧公钥验不过

        String newFp = rotate.newFingerprint();
        record(newFp, old.displayName(), rotate.newPubkey(), now);
        Author n = byFingerprint.get(newFp);
        byFingerprint.put(newFp, new Author(newFp, old.displayName(), n.firstSeen(),
                old.apps(), true, false, n.pubkeyBase64()));
        for (String appId : old.apps()) appAuthor.put(appId, newFp);
        return newFp;
    }

    public java.util.Collection<Author> authors() {
        return byFingerprint.values();
    }

    // ---------------------------------------------------------------- 落盘

    /** 读。文件不在或者坏了都从空的开始 —— 信任库坏了不该让玩家装不了任何东西。 */
    public static TrustStore load(Path file) {
        TrustStore store = new TrustStore();
        if (!Files.isRegularFile(file)) return store;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var parsed = JsonParser.parseReader(r);
            if (!parsed.isJsonObject()) return store;
            JsonObject root = parsed.getAsJsonObject();
            JsonObject authors = root.has("authors") ? root.getAsJsonObject("authors") : new JsonObject();
            for (String fp : authors.keySet()) {
                JsonObject a = authors.getAsJsonObject(fp);
                List<String> apps = new ArrayList<>();
                if (a.has("apps")) for (var e : a.getAsJsonArray("apps")) apps.add(e.getAsString());
                store.byFingerprint.put(fp, new Author(fp,
                        a.has("name") ? a.get("name").getAsString() : "",
                        a.has("firstSeen") ? a.get("firstSeen").getAsLong() : 0,
                        List.copyOf(apps),
                        a.has("trusted") && a.get("trusted").getAsBoolean(),
                        a.has("blocked") && a.get("blocked").getAsBoolean(),
                        a.has("pubkey") ? a.get("pubkey").getAsString() : ""));
                for (String appId : apps) store.appAuthor.put(appId, fp);
            }
        } catch (IOException | RuntimeException e) {
            com.november.mcphone.MCphone.LOGGER.warn("[MCphone] 信任库读不了 {}: {}", file, e.toString());
        }
        return store;
    }

    /** 写。 */
    public void save(Path file) {
        JsonObject authors = new JsonObject();
        for (Author a : byFingerprint.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", a.displayName());
            o.addProperty("firstSeen", a.firstSeen());
            o.addProperty("trusted", a.trusted());
            o.addProperty("blocked", a.blocked());
            o.addProperty("pubkey", a.pubkeyBase64());
            JsonArray apps = new JsonArray();
            for (String s : a.apps()) apps.add(s);
            o.add("apps", apps);
            authors.add(a.fingerprint(), o);
        }
        JsonObject root = new JsonObject();
        root.add("authors", authors);
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(root.toString());
            }
        } catch (IOException e) {
            com.november.mcphone.MCphone.LOGGER.warn("[MCphone] 信任库写不了 {}: {}", file, e.toString());
        }
    }
}
