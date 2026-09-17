package com.november.mcphone.core.script.server.store;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户端这一侧的保险箱（施工方案 §17.4.2、§17.4.4）。<b>口令与派生密钥永远不离开这里。</b>
 *
 * <h2>防回滚</h2>
 *
 * GCM 挡得住篡改，<b>挡不住回滚</b>：服主把存档还原到旧版本，就能把你的 token 换回一个旧值 ——
 * 那份旧密文的 AAD、标签、一切都是对的。
 *
 * <p>所以每条记录带单调递增的 {@code recordVersion}，客户端<b>在本地</b>记住每个 key 见过的最大值；
 * 服务端给的比本地记的小就<b>报错并拒绝使用，不静默接受</b>。
 *
 * <p>本地那份版本表存在 {@code local} 档里 —— <b>这是 sealed 依赖 local 的唯一一处</b>（§17.4.2）。
 *
 * <h2>口令</h2>
 *
 * 口令不进任何网络包、不写盘；派生出来的密钥<b>按会话保留在内存里，关手机即丢</b>。
 * <b>忘了口令 = 数据永久丢失</b>：不提供恢复、不提供提示问题、不提供服主重置。
 */
public final class VaultClient {

    /** 版本倒退时给玩家看的本地化键。<b>不是文本</b>。 */
    public static final String KEY_ROLLBACK = "mcphone.vault.rollback";

    /** 解不开时的本地化键。 */
    public static final String KEY_BAD_TAG = "mcphone.vault.bad_tag";

    /** 还没设口令。 */
    public static final String KEY_LOCKED = "mcphone.vault.locked";

    /** 保险箱用不了时抛它。<b>带的是本地化键，不是 Java 栈</b>（§16.6）。 */
    public static final class VaultException extends RuntimeException {
        private final String messageKey;

        public VaultException(String messageKey, String detail) {
            super(messageKey + ": " + detail);
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    private final String serverId;
    private final String playerUuid;

    /** key → 见过的最大 recordVersion。真正的持久化在 local 档，这里是会话内的镜像。 */
    private final Map<String, Long> known = new HashMap<>();

    /** 派生出来的密钥。<b>只在内存里，关手机就该 {@link #lock()}</b>。 */
    private SecretKey sessionKey;

    public VaultClient(String serverId, String playerUuid) {
        this.serverId = serverId;
        this.playerUuid = playerUuid;
    }

    /**
     * 版本判定（§17.4.2）。<b>{@code docs/VaultTest.java} 直接断言它。</b>
     *
     * @param serverVersion 服务端这条记录说自己是第几版
     * @param localKnown    本地记得的最大版本，没见过是 0
     */
    public static boolean accept(long serverVersion, long localKnown) {
        return serverVersion >= localKnown;
    }

    /** 从 local 档把版本表读回来。 */
    public void restoreVersions(Map<String, Long> fromLocal) {
        known.clear();
        known.putAll(fromLocal);
    }

    /** 交给 local 档存起来。 */
    public Map<String, Long> versionsForLocal() {
        return Map.copyOf(known);
    }

    /** 用口令开箱。口令用完当场抹掉。 */
    public void unlock(char[] passphrase, byte[] salt) {
        if (!VaultCrypto.passphraseLongEnough(passphrase)) {
            throw new VaultException(KEY_LOCKED, "口令至少 " + VaultCrypto.MIN_PASSPHRASE + " 个字符");
        }
        try {
            sessionKey = VaultCrypto.derive(passphrase, salt);
        } finally {
            VaultCrypto.wipe(passphrase);
        }
    }

    /** 关手机时叫。密钥不写盘，所以丢掉就是丢掉。 */
    public void lock() {
        sessionKey = null;
    }

    public boolean unlocked() {
        return sessionKey != null;
    }

    /** 把一个值封成记录，准备交给服务端搬运。 */
    public SealedRecord put(String appId, String key, String plaintext, byte[] salt) {
        requireUnlocked();
        long next = known.getOrDefault(key, 0L) + 1;
        byte[] aad = VaultCrypto.aad(serverId, playerUuid, appId, key, SealedRecord.SCHEMA, next);
        VaultCrypto.Sealed sealed = VaultCrypto.seal(sessionKey, aad, plaintext.getBytes(StandardCharsets.UTF_8));
        known.put(key, next);
        return new SealedRecord(salt, sealed.nonce(), sealed.cipher(), SealedRecord.SCHEMA, next);
    }

    /**
     * 把服务端搬回来的记录解开。
     *
     * <p>两条都会抛，<b>都不会静默返回空</b>：静默为空会让"服主把别人的密文塞进来"
     * 表现成"数据没了"，而玩家不会去查。
     */
    public String get(String appId, String key, SealedRecord record) {
        requireUnlocked();
        long localKnown = known.getOrDefault(key, 0L);
        if (!accept(record.recordVersion(), localKnown)) {
            throw new VaultException(KEY_ROLLBACK,
                    "服务端给的是第 " + record.recordVersion() + " 版，本地见过第 " + localKnown + " 版");
        }
        byte[] aad = VaultCrypto.aad(serverId, playerUuid, appId, key,
                record.schemaVersion(), record.recordVersion());
        try {
            byte[] plain = VaultCrypto.unseal(sessionKey,
                    aad, new VaultCrypto.Sealed(record.nonce(), record.cipher()));
            known.put(key, record.recordVersion());
            return new String(plain, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            // 口令不对、AAD 六个字段里任何一个不对、密文被改过，都落在这里
            throw new VaultException(KEY_BAD_TAG, key);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new VaultException(KEY_BAD_TAG, key);
        }
    }

    private void requireUnlocked() {
        if (sessionKey == null) throw new VaultException(KEY_LOCKED, "保险箱还锁着");
    }
}
