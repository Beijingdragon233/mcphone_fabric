package com.november.mcphone;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 【工具链探针，不是本模组在 26.3 上的入口】。
 *
 * 这个文件的存在理由只有一条：让 javac 真的去解析几个 26.x 的【新名字】。
 * 骨架阶段不挂 shared/ 与共享层，src/main/java 下一个文件都没有的话，
 * <code>compileJava</code> 会直接 NO-SOURCE —— 那样就算构建全绿也说明不了任何事：
 * 依赖解析过了、原版 jar 也拿到了，但没有任何一行代码证明【名字】能用。
 *
 * 挑的三个名字，每一个都在回答一个不同的问题：
 *
 * <ul>
 *   <li>{@link Identifier} —— 26.x 把 {@code ResourceLocation} 改成了这个名字。
 *       它是"Mojang 官方名到底有没有到 javac 眼前"最省事的探针：26.3 的 client jar
 *       已经不混淆（那个 jar 里 11266 个 {@code net/minecraft/**} 条目、0 个
 *       {@code class_N}，而 1.21.1 那边是混淆的短随机名；piston-meta 里 26.3 的
 *       downloads 也只剩 client 与 server，没有 client_mappings），所以这一支要验的
 *       不是"拉不拉得到映射"，而是"ModDevGradle 吃不吃一个已经命名好的输入 jar"。</li>
 *   <li>{@link GuiGraphicsExtractor} —— 26.1 起 {@code GuiGraphics} 换的名字。
 *       它在 {@code net.minecraft.client.**} 底下，引它等于顺带确认客户端那一半的
 *       工件也在编译类路径上 —— 本模组 201 个文件要改，绝大多数卡在这套自绘 UI 上。</li>
 *   <li>{@link FMLCommonSetupEvent} 加 {@link SubscribeEvent} —— 加载器自己的 API。
 *       原版名字对了但 NeoForge 的注解与事件包不对，一样是零分。</li>
 * </ul>
 *
 * <p>下一步（把共用代码接上来那一步）会用真正的入口类替换这个文件：它带的注册、
 * 网络包、配置与 App 那一整套，才是 26.3 上要改的东西。
 */
@Mod(MCphone.MOD_ID)
public final class MCphone {

    public static final String MOD_ID = "mcphone";

    public MCphone() {
    }

    @SubscribeEvent
    public void onCommonSetup(FMLCommonSetupEvent event) {
        // 只在开发环境的启动里说一句话：jar 能装进 26.3 的 FML 并且跑到这个回调，
        // 就是"加载器侧接上了"的现场证据，而这件事构建本身看不见。
        event.enqueueWork(() -> System.out.println(
                "[mcphone/26.3 骨架] 工具链探针已加载：" + probeNamespace() + " / UI 类型 " + probeUiType().getSimpleName()));
    }

    /**
     * 走一遍 {@code Identifier} 的工厂方法。静态方法调用是编译期就要符号解析的，
     * 所以这一行既证明类在，也证明【方法形状】没变（26.x 只是改名，
     * {@code fromNamespaceAndPath} 这一族还在）。
     */
    static String probeNamespace() {
        return Identifier.fromNamespaceAndPath(MOD_ID, "toolchain").toString();
    }

    /**
     * 只取 {@code Class} 对象，不碰它的任何方法 —— 骨架阶段要的是"这个类型解析得到"，
     * 而不是它的 API 形状，那要等自绘 UI 那一层真 port 过来时再验。
     */
    static Class<?> probeUiType() {
        return GuiGraphicsExtractor.class;
    }
}
