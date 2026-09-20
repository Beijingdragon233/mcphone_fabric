package com.november.mcphone;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * MCphone —— NeoForge 26.3 这一支的入口。
 *
 * 【眼下它只交出三个名字】：{@code MODID}、{@code LOGGER}、{@code getVersion()}。
 * 那不是省事，是移植这一步该做的事：共用代码里有 279 条错误挂在这三个符号上
 * （{@code LOGGER} 216 处 + {@code MODID} 61 处 + {@code getVersion()} 2 处，
 * 摊在 109 个文件上，占上一轮那份日志 1523 条的 18%）。三个名字一齐，那 279 条
 * 当场消失，剩下的错误才读得动。计数过程见 docs/PORTING-26.3.md。
 *
 * 这个位置上原先站的是一份【工具链探针】。它唯一的作用是让 javac 去解析几个 26.x
 * 的新名字（{@code Identifier}、{@code GuiGraphicsExtractor}），好回答"ModDevGradle
 * 喂不喂得动 26.3 那份已经命名好的输入 jar"。那件事 2026-09-19 答完了，结论记在
 * versions/targets.json 的 26.3-neoforge 那条 note 里，探针本体就跟着退场。
 *
 * <p><b>为什么不顺手把注册那一整段从 1.21.1 抄过来</b>
 *
 * <p>那一段引用的类在这一支【一个都还没有】：它们全是每平台一份的文件，而
 * {@code platforms/26.3-neoforge/src/main/java} 到这次提交为止只有这一个文件。
 * 现在写上去换来的不是功能，是十几行 {@code cannot find symbol} 混进错误直方图，
 * 而这一步要的恰恰是一份干净的直方图。所以那一段等它依赖的文件到位时一行一行回来，
 * 下面这份清单是账，别靠记忆。
 *
 * <p><b>【待接回清单】</b>对照 {@code platforms/1.21.1-neoforge} 那份入口逐条列出，
 * 每条注明等谁：
 *
 * <ul>
 *   <li>{@code ModItems} / {@code ModDataComponents} / {@code ModCreativeTabs} /
 *       {@code ModMenus} / {@code ModAttachments} / {@code ModSounds} 六个
 *       {@code DeferredRegister.register(modEventBus)} —— 等各那个平台文件</li>
 *   <li>{@code modEventBus.addListener(NetworkHandler::register)} —— 等平台文件，
 *       注意它【必须留在构造期】，理由见 1.20.1 那份入口里那段注释</li>
 *   <li>{@code modContainer.registerConfig(Type.SERVER, ServerConfig.SPEC, ...)}
 *       —— 等平台文件</li>
 *   <li>{@code ServerStartedEvent -> EconomyRuntime.start(e.getServer())}、
 *       停服那侧的 {@code EconomyRuntime.stop()}、
 *       {@code ServerTickEvent.Post -> EconomyRuntime.tick()}（超时托管每 5 分钟扫一次）、
 *       {@code RegisterCommandsEvent -> EconomyCommand.register(e.getGenerator())} 四条
 *       —— 这四条是 main 侧 S15e（{@code 71e481c} 与 {@code 14e1be6}）加进 1.21.1 那份入口的，
 *       补进来时顺带把 1.21.1 那段【先关货币网关、再停 worker】的次序注释一起带过来；
 *       等的是 {@code EconomyRuntime} 与 {@code EconomyCommand} 把错误清完（两个都在挂载里）</li>
 *   <li>{@code TerminalCharger} / {@code DiscService} / {@code CompatModules} /
 *       {@code MCphoneClient} 那几条游戏总线与客户端 init —— 各等平台文件</li>
 *   <li>{@code RequestThrottle} / {@code ChatImageUploads} / {@code ChatImageStore} /
 *       {@code Terminals} 四个与 {@code ScriptWorkers} 那一对【已经在 26.3 的挂载里】
 *       —— 前四个来自 {@code layers/loader/neoforge}，{@code ScriptWorkers} 来自
 *       {@code shared/}。它们本体清完错误就能接回来，是这份清单里最早能还的账</li>
 * </ul>
 *
 * <p>构造函数的形状不是照抄 1.21.1 的，26.3 上另取过一次证（2026-09-19，
 * {@code neoforge-26.3.0.3-beta-sources.jar} 与 {@code loader-12.0.0.jar}）：
 * {@code NeoForgeMod} 的构造是 {@code (IEventBus, Dist, ModContainer)}、
 * {@code ClientNeoForgeMod} 是 {@code (IEventBus, ModContainer)}，注入这条路还在；
 * {@code @Mod} 的 {@code @Target} 仍只有 {@code TYPE}；
 * {@code ModContainer.getModInfo()} 返回 {@code IModInfo}，它的
 * {@code getVersion()} 是 {@code ArtifactVersion}，所以 {@code toString()}
 * 那一句照旧成立。
 *
 * <p>{@code modEventBus} 这一行还没用上它。留着是因为它既是注入形状的一部分，
 * 也是上面那六条 {@code DeferredRegister} 与网络包的落点。
 */
@Mod(MCphone.MODID)
public final class MCphone {

    /**
     * 共用代码认的是这个名字。探针那一份写的叫 {@code MOD_ID}，
     * 而挂载里 56 个文件写死了 {@code MCphone.MODID} —— 名字也是接口的一部分。
     */
    public static final String MODID = "mcphone";

    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 运行时的真实版本号，tooltip 用它填 %s，语言文件里不写死。
     * 在构造函数里由 modContainer 赋值，不用 ModList 静态查询 ——
     * 那依赖 FML 的类加载时序，取不到时是静默的空值。
     */
    private static String version = "";

    public MCphone(IEventBus modEventBus, ModContainer modContainer) {
        version = modContainer.getModInfo().getVersion().toString();

        // 【这一行是这一支眼下全部的实际行为】：它证明 jar 装进了 26.3 的 FML、
        // 实例构造成功、版本号取到了。注册那一段在下面那份清单里，一条一条回来。
        LOGGER.info("[MCphone] NeoForge 26.3 已加载 v{}", version);
    }

    /** 本模组版本号，如 "1.10.2"。模组构造前调用会得到空串。 */
    public static String getVersion() {
        return version;
    }
}
