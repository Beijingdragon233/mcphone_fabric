package com.november.mcphone.core.script.server.economy;

import java.util.Map;
import java.util.UUID;

/**
 * builtin 提供者把余额存在哪（施工方案 §22.7）。
 *
 * <p>抽成接口是为了能测：真正的实现落在 {@code PhonePlayerData.economy()} 上，
 * 而那要一台服务器；断言测试喂一个内存实现，就能把 §22.9 的五条不变量与守恒逐条判掉。
 *
 * <p><b>落盘不许另造第三套机制</b>（勘误 E15）：走 S14 建的那条具名访问器的路。
 */
public interface BalanceStore {

    long get(UUID player, String currencyId);

    void set(UUID player, String currencyId, long value);

    /** 对账要把所有玩家的余额加起来（§22.10）。离线玩家也要算进去。 */
    Map<UUID, Long> all(String currencyId);
}
