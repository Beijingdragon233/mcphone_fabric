package com.november.mcphone.core.script.net;

import com.november.mcphone.MCphone;
import com.november.mcphone.core.script.server.PlayerSnapshot;
import com.november.mcphone.core.script.server.ScriptPipeline;
import com.november.mcphone.platform.Profiles;
import net.minecraft.server.level.ServerPlayer;

/**
 * 三个平台的登记点都指到这里（施工方案 §15.5）。<b>调到这里时已经在服务器主线程上</b> ——
 * 三个门面各自用 {@code consumerMainThread} / {@code ctx.enqueueWork} / {@code server.execute} 保证。
 *
 * <h2>本步还没有真正的管线</h2>
 *
 * {@link ScriptPipeline} 要一张已批准的部署表与一张授权表，那两张是 S17 的交付物（§14.4）。
 * 所以 {@link #pipeline} 在本步<b>始终是 null</b>，任何请求一律回 {@link ScriptErrorCode#NOT_DEPLOYED} ——
 * 那正是"本服没有这个 App 的已批准部署"的字面意思，不是兜底。
 *
 * <p>登记本身现在就要做：三个包的序号由注册顺序发放（§10.3 顺序即身份），
 * 等 S17 到货再登记的话，那时追加的序号与现在追加的不是同一个。
 */
public final class ScriptRpcHandler {

    private ScriptRpcHandler() {
    }

    /** S17 装好部署表与授权表之后把它塞进来。 */
    private static volatile ScriptPipeline pipeline;

    public static void install(ScriptPipeline p) {
        pipeline = p;
    }

    /** 服务器停止时清掉 —— 单人游戏里下一个世界不该看见上一个世界的管线。 */
    public static void clear() {
        pipeline = null;
    }

    /** 由各平台的 {@code ScriptNetworking} 指过来。 */
    public static void handle(ScriptRpc rpc, ServerPlayer player) {
        ScriptPipeline p = pipeline;
        if (p == null) {
            send(player, ScriptRpcResult.fail(rpc.requestId(), ScriptErrorCode.NOT_DEPLOYED));
            return;
        }
        PlayerSnapshot snapshot = new PlayerSnapshot(
                player.getUUID(),
                Profiles.name(player.getGameProfile()),
                player.level().dimension().location().toString(),
                player.gameMode.getGameModeForPlayer().getName(),
                0L);
        p.accept(rpc, snapshot, result -> send(player, result));
    }

    /**
     * 把结果发回去。各平台的发包函数签名不同（1.20.1 收 {@code Object}，1.21.1 收
     * {@code CustomPacketPayload}），所以由各平台的 {@code ScriptNetworking} 装一个发送器进来。
     */
    private static volatile java.util.function.BiConsumer<ServerPlayer, ScriptRpcResult> sender;

    public static void installSender(java.util.function.BiConsumer<ServerPlayer, ScriptRpcResult> s) {
        sender = s;
    }

    private static void send(ServerPlayer player, ScriptRpcResult result) {
        java.util.function.BiConsumer<ServerPlayer, ScriptRpcResult> s = sender;
        if (s == null) {
            MCphone.LOGGER.warn("[MCphone] 脚本 RPC 的发送器还没装，结果发不出去: {}", result.code());
            return;
        }
        s.accept(player, result);
    }
}
