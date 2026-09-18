package com.november.mcphone.core.script.server.economy;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Set;

/**
 * {@code /mcphone economy audit}（§22.10）：把每种货币的账对一遍，<b>不平的那一行要显眼</b>。
 *
 * <p>要 OP 3 级：余额是所有人的隐私，对账结果本身也会暴露服务器的经济规模。
 * 别的 {@code /mcphone} 子命令各自注册自己的一支就行 —— brigadier 会把同名的字面节点并到一起。
 */
public final class EconomyCommand {

    private EconomyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mcphone")
                .requires(src -> src.hasPermission(3))
                .then(Commands.literal("economy")
                        .then(Commands.literal("audit").executes(ctx -> audit(ctx.getSource())))));
    }

    private static int audit(CommandSourceStack src) {
        EconomyRuntime rt = EconomyRuntime.current();
        if (rt == null) {
            src.sendFailure(Component.literal("[对账] 货币系统没在运行"));
            return 0;
        }
        EconomyData data = rt.data();
        if (data.wholeLock() != null) {
            src.sendFailure(Component.literal("[对账] ⚠⚠ 货币存档整份锁住了，没法对账：" + data.wholeLock()));
            return 0;
        }
        Set<String> ids = data.currencyIds();
        if (ids.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[对账] 还没有任何货币的账"), false);
            return 1;
        }
        Set<String> locked = data.lockedCurrencies();
        int unbalanced = 0;
        for (String id : ids) {
            if (locked.contains(id)) {
                src.sendFailure(Component.literal("[对账] ⚠⚠ " + id + " 的存档读坏了、锁住了，跳过（细节见服务器日志）"));
                unbalanced++;
                continue;
            }
            // 一律按 builtin 档对：余额取自世界存档。计分板档的余额不在这份存档里，对出来必然不平 ——
            // 注册表接进来（S15f）之后要按档区分，在那之前生产环境里还没有计分板档的货币
            EconomyAudit.Result r = EconomyAudit.run(id, data);
            if (!r.balanced()) unbalanced++;
            Component line = Component.literal(r.describe())
                    .withStyle(r.balanced() ? ChatFormatting.GREEN : ChatFormatting.RED);
            src.sendSuccess(() -> line, false);
        }
        return unbalanced == 0 ? 1 : 0;
    }
}
