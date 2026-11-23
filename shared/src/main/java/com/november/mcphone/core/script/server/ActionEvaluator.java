package com.november.mcphone.core.script.server;

import com.google.common.collect.ImmutableList;

import com.november.mcphone.core.script.net.ScriptErrorCode;

import java.util.List;
import java.util.function.Consumer;

/**
 * 跑一次脚本动作（施工方案 §15.5 的 worker 那一层）。<b>S13 用 Rhino 实现，本步只定接口。</b>
 *
 * <h2>为什么不是 {@code Outcome evaluate(Request, Snapshot)} 这种纯函数</h2>
 *
 * 那个形状 S13 装不进去。§16.5 给脚本的 {@code ctx} 里全是<b>读写且要持久化</b>的东西
 * （{@code ctx.store.setLong}、{@code ctx.shared.compareAndSet}、{@code ctx.currency.pay}），
 * 而 §20.4 写死"{@code compareAndSet} 在主线程上做"。脚本是命令式的：
 * {@code if (ctx.shared.compareAndSet(...)) ...} 的返回值决定后面走哪一支 ——
 * 没法拆成"worker 上纯求值 → 主线程落地意图"两段，第一段要拿到第二段的结果才能继续。
 *
 * <p>所以<b>接口不承诺线程</b>，把线程归属关进实现：实现自己在 worker 与主线程之间来回，
 * 只保证 {@code onDone} 在主线程上被调到<b>一次且仅一次</b>。
 *
 * <h2>这是内部接口，可以改签名</h2>
 *
 * {@code MCphoneApi} 那五条"只增不减"的规矩第五条写着"只管 api 包"。
 * 这个类住在 {@code core/script/server/}，没有外部实现者。
 * <b>但它不许搬到 {@code api/} 下</b> —— 搬过去就等于把一个还会变的形状冻起来。
 */
public interface ActionEvaluator {

    /**
     * 提交一次求值。
     *
     * @param onDone <b>收下了就保证在服务器主线程上被调到一次且仅一次</b>。实现不许调两次，
     *               也不许一次都不调 —— 一次都不调会把 §15.1 那 4 个并发槽永久烧掉一个
     * @return 收没收下。<b>false = 忙，{@code onDone} 不会被调</b>，调用方据此回
     *         {@link ScriptErrorCode#RATE_LIMITED}。
     *         有界队列在实现那一侧（{@link ScriptWorkers}），但背压要传回准入这一层 ——
     *         不传的话队列满时只能静默丢弃，客户端干等到超时，那 4 个并发槽就白烧了
     */
    boolean submit(Request request, Consumer<Outcome> onDone);

    /**
     * @param seq 这一次求值的序号，只用于日志与排查。<b>不是幂等键</b>
     */
    final class Request {
        private final String appId;
        private final String actionId;
        private final byte[] params;
        private final PlayerSnapshot player;
        private final String deployRev;
        private final long seq;

        public Request(String appId, String actionId, byte[] params, PlayerSnapshot player, String deployRev, long seq) {
            this.appId = appId;
            this.actionId = actionId;
            this.params = params;
            this.player = player;
            this.deployRev = deployRev;
            this.seq = seq;
        }

        public String appId() { return appId; }

        public String actionId() { return actionId; }

        public byte[] params() { return params; }

        public PlayerSnapshot player() { return player; }

        public String deployRev() { return deployRev; }

        public long seq() { return seq; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Request)) return false;
            Request that = (Request) other;
            return java.util.Objects.equals(this.appId, that.appId)
                    && java.util.Objects.equals(this.actionId, that.actionId)
                    && java.util.Objects.equals(this.params, that.params)
                    && java.util.Objects.equals(this.player, that.player)
                    && java.util.Objects.equals(this.deployRev, that.deployRev)
                    && this.seq == that.seq;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(appId);
            hash = 31 * hash + java.util.Objects.hashCode(actionId);
            hash = 31 * hash + java.util.Objects.hashCode(params);
            hash = 31 * hash + java.util.Objects.hashCode(player);
            hash = 31 * hash + java.util.Objects.hashCode(deployRev);
            hash = 31 * hash + java.util.Objects.hashCode(seq);
            return hash;
        }

        @Override public String toString() { return "Request[appId=" + appId + ", actionId=" + actionId + ", params=" + params + ", player=" + player + ", deployRev=" + deployRev + ", seq=" + seq + "]"; }

    }

    /**
     * 求值的结论。
     *
     * <p>{@code retryAfterMs} 与 {@code stateRevision} 是 §15.3 的 S2C 字段，
     * <b>它们的值只有业务层知道</b>（{@code COOLDOWN} 的 nextAt、{@code EXHAUSTED} 的余量、
     * §20.6 的 revision）。不放在这里，S13 到货时就要改这个已经合并的签名。
     */
    final class Outcome {
        private final ScriptErrorCode code;
        private final byte[] data;
        private final String messageKey;
        private final List<String> messageArgs;
        private final long retryAfterMs;
        private final long stateRevision;
        private final List<ActionIntent> intents;
        private final boolean moneyMoved;

        public ScriptErrorCode code() { return code; }

        public byte[] data() { return data; }

        public String messageKey() { return messageKey; }

        public List<String> messageArgs() { return messageArgs; }

        public long retryAfterMs() { return retryAfterMs; }

        public long stateRevision() { return stateRevision; }

        public List<ActionIntent> intents() { return intents; }

        public boolean moneyMoved() { return moneyMoved; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Outcome)) return false;
            Outcome that = (Outcome) other;
            return java.util.Objects.equals(this.code, that.code)
                    && java.util.Objects.equals(this.data, that.data)
                    && java.util.Objects.equals(this.messageKey, that.messageKey)
                    && java.util.Objects.equals(this.messageArgs, that.messageArgs)
                    && this.retryAfterMs == that.retryAfterMs
                    && this.stateRevision == that.stateRevision
                    && java.util.Objects.equals(this.intents, that.intents)
                    && this.moneyMoved == that.moneyMoved;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(code);
            hash = 31 * hash + java.util.Objects.hashCode(data);
            hash = 31 * hash + java.util.Objects.hashCode(messageKey);
            hash = 31 * hash + java.util.Objects.hashCode(messageArgs);
            hash = 31 * hash + java.util.Objects.hashCode(retryAfterMs);
            hash = 31 * hash + java.util.Objects.hashCode(stateRevision);
            hash = 31 * hash + java.util.Objects.hashCode(intents);
            hash = 31 * hash + java.util.Objects.hashCode(moneyMoved);
            return hash;
        }

        @Override public String toString() { return "Outcome[code=" + code + ", data=" + data + ", messageKey=" + messageKey + ", messageArgs=" + messageArgs + ", retryAfterMs=" + retryAfterMs + ", stateRevision=" + stateRevision + ", intents=" + intents + ", moneyMoved=" + moneyMoved + "]"; }


        public Outcome(ScriptErrorCode code, byte[] data, String messageKey, List<String> messageArgs, long retryAfterMs, long stateRevision, List<ActionIntent> intents, boolean moneyMoved) {
            if (code == null) code = ScriptErrorCode.INTERNAL;
            if (data == null) data = new byte[0];
            if (messageKey == null) messageKey = code.defaultMessageKey();
            messageArgs = messageArgs == null ? ImmutableList.of() : ImmutableList.copyOf(messageArgs);
            intents = intents == null ? ImmutableList.of() : ImmutableList.copyOf(intents);
            this.code = code;
            this.data = data;
            this.messageKey = messageKey;
            this.messageArgs = messageArgs;
            this.retryAfterMs = retryAfterMs;
            this.stateRevision = stateRevision;
            this.intents = intents;
            this.moneyMoved = moneyMoved;
        }

        /**
         * 这一次求值里<b>钱已经动过</b>（provider 返回过结果）。落地前重查被拒时要据此回
         * {@code UNKNOWN} 而不是 {@code NOT_AUTHORIZED} —— 回后者玩家会以为"没动、重试一下"，
         * 而钱可能已经付了（E35③、§15.9）。
         */
        public Outcome withMoneyMoved() {
            return moneyMoved ? this
                    : new Outcome(code, data, messageKey, messageArgs, retryAfterMs, stateRevision, intents, true);
        }

        public static Outcome ok(byte[] data, long stateRevision, List<ActionIntent> intents) {
            return new Outcome(ScriptErrorCode.OK, data, "", ImmutableList.of(), 0, stateRevision, intents, false);
        }

        public static Outcome fail(ScriptErrorCode code) {
            return new Outcome(code, new byte[0], code.defaultMessageKey(), ImmutableList.of(), 0, 0, ImmutableList.of(), false);
        }
    }
}
