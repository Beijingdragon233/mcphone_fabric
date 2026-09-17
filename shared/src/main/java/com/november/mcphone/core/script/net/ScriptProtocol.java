package com.november.mcphone.core.script.net;

/**
 * 脚本 RPC 的线上常量（施工方案 §15.3、§10.3）。<b>改这里的任何一个数都是改线格式。</b>
 */
public final class ScriptProtocol {

    private ScriptProtocol() {
    }

    /**
     * 脚本 RPC 自己的协议号，与加载器的握手版本无关。
     *
     * <p><b>它不是冗余的</b>：三个平台里只有两个有加载器级的版本闸
     * （1.20.1-forge 的 {@code PROTOCOL_VERSION = "5"}、1.21.1-neoforge 的 {@code registrar("2")}），
     * <b>1.21.1-fabric 一个都没有</b>（全平台 grep 零命中）。Fabric 上这个字段就是唯一的版本闸。
     *
     * <p>所以对不上时必须回 {@link ScriptErrorCode#VERSION_MISMATCH}，<b>不许断线</b> ——
     * 断线在 Fabric 上会把版本不一致表现成"连不上服务器"，玩家无从知道该更新。
     */
    public static final int PROTOCOL = 1;

    /** {@code params} 与 {@code data} 的上限（§15.3）。解码侧必须带着它读，否则是内存放大面。 */
    public static final int PARAMS_MAX = 4096;

    /** 同上。 */
    public static final int DATA_MAX = 4096;

    /** {@code appId} / {@code deployRev} / {@code actionId} 的字符上限（§10.3 的示例就是 64）。 */
    public static final int ID_MAX = 64;

    /** {@code messageKey} 的字符上限。本地化键，不是文本。 */
    public static final int KEY_MAX = 128;

    /** {@code messageArgs} 的条数与单条长度上限。 */
    public static final int ARGS_MAX = 8;
    public static final int ARG_LEN_MAX = 128;

    /** {@code frontendDigest} 的字符上限：sha256 的十六进制是 64 位。 */
    public static final int DIGEST_MAX = 64;

    /** {@code topic} 的字符上限（§15.3 的 script_push）。 */
    public static final int TOPIC_MAX = 128;

    // ---------------------------------------------------------------- 宿主保留的命名空间

    /**
     * 宿主自己发的推送用这个 appId（§13.5、§13.8）。
     *
     * <p><b>为什么要保留</b>：握手（serverId / 部署表 / 已授权动作）走 {@code script_push}，
     * 而 {@code topic} 在 §16.5 里是<b>脚本说了算</b>的字符串（{@code ctx.notify(topic, data)}）。
     * 不保留的话，任何一个普通 App 的 {@code server.js} 写一句
     * {@code ctx.notify('mcphone:handshake/begin', …)} 就能把 serverId 与部署表整个改写。
     *
     * <p>两道闸，缺一不可：
     * <ol>
     *   <li>装包时拒绝 {@code mcphone} 命名空间的 appId（{@code Manifest.RESERVED_NAMESPACE} 已经在拦）</li>
     *   <li>脚本侧的 {@code ctx.notify} 只许发以自己 appId 开头的 topic（<b>S13 落实</b>，本步只定常量）</li>
     * </ol>
     * 客户端分派握手时两个条件都要查：appId 等于这个，且 topic 以 {@link #HOST_TOPIC_PREFIX} 开头。
     */
    public static final String HOST_APP_ID = "mcphone:host";

    /** 宿主保留的 topic 前缀。脚本发的 topic 一律拒这个前缀。 */
    public static final String HOST_TOPIC_PREFIX = "mcphone:";

    /** 握手的三个 topic，见 {@link Handshake}。 */
    public static final String TOPIC_HANDSHAKE_BEGIN = "mcphone:handshake/begin";
    public static final String TOPIC_HANDSHAKE_DEPLOYMENT = "mcphone:handshake/deployment";
    public static final String TOPIC_HANDSHAKE_END = "mcphone:handshake/end";

    /**
     * 一次握手最多推几个部署。
     *
     * <p>握手<b>一个部署一条 push</b>，不把整张表塞进一个 4 KiB 的 data ——
     * 按字段上限算，一个部署要 815 字节，4 KiB 只装得下 4 个；按典型值算 20 个部署是 4466 字节，
     * 也超。分片则要在客户端加一套重组状态机，凭空多一个内存放大面。
     *
     * <p>封顶在这里是为了另一头：一个装了 200 个 App 的服务器不该在玩家进服的那一刻推 200 个包。
     * 超过就只推 {@link Handshake.Begin}（带总数），明细由客户端按需用 {@code script_rpc} 拉
     * （{@link #HOST_APP_ID} + {@code actionId = "deployments"}），不必加第四个包。
     */
    public static final int HANDSHAKE_MAX_DEPLOYMENTS = 64;

    /** 一个部署最多声明几个动作。握手按这个数算得出单条 push 的上限。 */
    public static final int MAX_ACTIONS_PER_DEPLOYMENT = 32;
}
