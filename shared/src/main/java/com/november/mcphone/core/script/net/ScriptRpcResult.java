package com.november.mcphone.core.script.net;

import com.google.common.collect.ImmutableList;

import com.november.mcphone.core.net.Wire;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/**
 * 服务端 → 客户端的一次调用结果（施工方案 §15.3 的 {@code script_rpc_result}）。
 *
 * <p>平台无关的那一半，说明见 {@link ScriptRpc}。
 *
 * @param requestId     对应哪一次 {@link ScriptRpc}
 * @param code          见 {@link ScriptErrorCode}。线上是序号，解码侧认不出<b>不抛</b>
 * @param data          结果，≤ {@link ScriptProtocol#DATA_MAX}
 * @param messageKey    <b>本地化键，不是文本</b>（§15.3）：服务端发键，客户端查自己的语言文件。
 *                      收文本就等于让后端往客户端推任意字符串，配合界面就是钓鱼面
 * @param messageArgs   本地化参数
 * @param retryAfterMs  {@link ScriptErrorCode#RATE_LIMITED} 时的退避毫秒数，其余为 0
 * @param stateRevision 业务状态版本（§20.6），客户端据此判断本地缓存过没过期
 */
public final class ScriptRpcResult {
    private final long requestId;
    private final ScriptErrorCode code;
    private final byte[] data;
    private final String messageKey;
    private final List<String> messageArgs;
    private final long retryAfterMs;
    private final long stateRevision;

    public long requestId() { return requestId; }

    public ScriptErrorCode code() { return code; }

    public byte[] data() { return data; }

    public String messageKey() { return messageKey; }

    public List<String> messageArgs() { return messageArgs; }

    public long retryAfterMs() { return retryAfterMs; }

    public long stateRevision() { return stateRevision; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ScriptRpcResult)) return false;
        ScriptRpcResult that = (ScriptRpcResult) other;
        return this.requestId == that.requestId
                && java.util.Objects.equals(this.code, that.code)
                && java.util.Objects.equals(this.data, that.data)
                && java.util.Objects.equals(this.messageKey, that.messageKey)
                && java.util.Objects.equals(this.messageArgs, that.messageArgs)
                && this.retryAfterMs == that.retryAfterMs
                && this.stateRevision == that.stateRevision;
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(data);
        hash = 31 * hash + java.util.Objects.hashCode(messageKey);
        hash = 31 * hash + java.util.Objects.hashCode(messageArgs);
        hash = 31 * hash + java.util.Objects.hashCode(retryAfterMs);
        hash = 31 * hash + java.util.Objects.hashCode(stateRevision);
        return hash;
    }

    @Override public String toString() { return "ScriptRpcResult[requestId=" + requestId + ", code=" + code + ", data=" + data + ", messageKey=" + messageKey + ", messageArgs=" + messageArgs + ", retryAfterMs=" + retryAfterMs + ", stateRevision=" + stateRevision + "]"; }


    public ScriptRpcResult(long requestId, ScriptErrorCode code, byte[] data, String messageKey, List<String> messageArgs, long retryAfterMs, long stateRevision) {
        if (code == null) code = ScriptErrorCode.INTERNAL;
        if (data == null) data = new byte[0];
        if (messageKey == null) messageKey = "";
        messageArgs = messageArgs == null ? ImmutableList.of() : ImmutableList.copyOf(messageArgs);
        this.requestId = requestId;
        this.code = code;
        this.data = data;
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
        this.retryAfterMs = retryAfterMs;
        this.stateRevision = stateRevision;
    }

    /** 成功，不带文案。 */
    public static ScriptRpcResult ok(long requestId, byte[] data, long stateRevision) {
        return new ScriptRpcResult(requestId, ScriptErrorCode.OK, data, "", ImmutableList.of(), 0, stateRevision);
    }

    /** 失败，用这个码的默认文案键。 */
    public static ScriptRpcResult fail(long requestId, ScriptErrorCode code) {
        return new ScriptRpcResult(requestId, code, new byte[0], code.defaultMessageKey(), ImmutableList.of(), 0, 0);
    }

    /** 限流，带退避。 */
    public static ScriptRpcResult rateLimited(long requestId, long retryAfterMs, String messageKey) {
        return new ScriptRpcResult(requestId, ScriptErrorCode.RATE_LIMITED, new byte[0],
                messageKey, ImmutableList.of(), retryAfterMs, 0);
    }

    public static void encode(ScriptRpcResult m, FriendlyByteBuf buf) {
        buf.writeVarLong(m.requestId());
        buf.writeVarInt(m.code().toWire());
        Wire.writeBytes(buf, m.data(), ScriptProtocol.DATA_MAX);
        buf.writeUtf(m.messageKey(), ScriptProtocol.KEY_MAX);
        Wire.writeList(buf, m.messageArgs(), ScriptProtocol.ARGS_MAX,
                (s, b) -> b.writeUtf(s, ScriptProtocol.ARG_LEN_MAX));
        buf.writeVarLong(m.retryAfterMs());
        buf.writeVarLong(m.stateRevision());
    }

    public static ScriptRpcResult decode(FriendlyByteBuf buf) {
        long requestId = buf.readVarLong();
        // 认不出的序号归 INTERNAL，不抛：抛在这里是 netty 解码异常 = 当场断线（见 ScriptErrorCode）
        ScriptErrorCode code = ScriptErrorCode.fromWire(buf.readVarInt());
        byte[] data = Wire.readBytes(buf, ScriptProtocol.DATA_MAX);
        String key = buf.readUtf(ScriptProtocol.KEY_MAX);
        List<String> args = Wire.readList(buf, ScriptProtocol.ARGS_MAX,
                b -> b.readUtf(ScriptProtocol.ARG_LEN_MAX));
        return new ScriptRpcResult(requestId, code, data, key, args, buf.readVarLong(), buf.readVarLong());
    }
}
