package com.november.mcphone.core.script.server.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * builtin 货币提供者的余额（施工方案 §22.7）。挂在 {@code PhonePlayerData.economy()} 上。
 *
 * <h2>为什么不塞进 {@code scriptKv()}</h2>
 *
 * <b>脚本写得到 {@code scriptKv}。</b>余额塞进去等于让脚本改自己的钱 ——
 * 与守卫计数必须单独一块是同一条理由（§19.2、§20.2）。
 *
 * <p>也不另造第三套持久化机制：走 S14 建的那条具名访问器的路（§10.4.1、勘误 E15）。
 *
 * <h2>余额必须死亡保留</h2>
 *
 * 1.20.1 的 {@code copyDeathPersistentFrom} 要带上它 —— 不带的话死一次钱就没了，
 * 而死亡在 Minecraft 里是随时可以自己安排的事。
 *
 * @param balances 货币 id → 余额，<b>最小单位的整数</b>（§22.3 ②，decimals=2 时 1234 是 12.34）
 */
public record ScriptEconomy(Map<String, Long> balances) {

    public static final ScriptEconomy DEFAULT = new ScriptEconomy(Map.of());

    public static final Codec<ScriptEconomy> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(Codec.STRING, Codec.LONG)
                            .fieldOf("balances").forGetter(ScriptEconomy::balances)
            ).apply(instance, ScriptEconomy::new)
    );

    public ScriptEconomy {
        balances = Map.copyOf(balances);
    }

    public long balance(String currencyId) {
        return balances.getOrDefault(currencyId, 0L);
    }

    public ScriptEconomy withBalance(String currencyId, long value) {
        Map<String, Long> next = new HashMap<>(balances);
        next.put(currencyId, value);
        return new ScriptEconomy(next);
    }
}
