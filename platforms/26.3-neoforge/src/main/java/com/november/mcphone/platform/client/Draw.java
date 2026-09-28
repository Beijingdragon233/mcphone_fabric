package com.november.mcphone.platform.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * 直接走顶点缓冲的那点绘制，以及 {@link Screen} 上换过签名的那几个方法 ——
 * 全仓唯一碰它们的地方。
 *
 * <h2>为什么值得一层</h2>
 *
 * 1.21 把顶点缓冲那套 API 整个改了名：{@code getBuilder() + begin()} 合成一个
 * {@code begin()}，{@code vertex/uv/color/endVertex} 变成
 * {@code addVertex/setUv/setColor}（不再需要收尾那一下），{@code end()} 变成
 * {@code build()}。同一个四边形，两支写出来没有一行是一样的。
 *
 * <b>这是判据看不见的那一类里最典型的</b>：类名没变、方法名换了、调用形状也变了，
 * 扫 import 与扫类型名都发现不了，只有真编一遍才知道。
 *
 * <h2>这个包放什么</h2>
 *
 * {@code platform.client} 下装的是「各目标做同一件事、但写法不同」【且碰客户端类型】
 * 的东西。<b>包名里那个 client 是硬要求</b>：dist 隔离那道闸只准路径里带 /client/ 的类
 * 引用 {@code net.minecraft.client.*}，别处引用了就会在专用服务器上一加载就崩服 ——
 * 这个类第一版放在 platform/ 下，当场被那道闸拦下。
 *
 * {@code platform} 下装的是「各目标做同一件事、但写法不同」的东西。
 * <b>不要往这里放业务逻辑</b> —— 「浏览器把网页画在哪一块」是业务，属于那个界面；
 * 「这个版本怎么提交一个带贴图的四边形」才是这里的事。
 *
 * <h2>这一支上 {@code texturedQuad} 【没有】，而且不是「还没写」</h2>
 *
 * 26.x 的界面绘制换成了【攒 render-state】：{@code GuiGraphicsExtractor} 的每个画的东西
 * 都是往 {@code GuiRenderState} 里塞一条状态（{@code innerFill} 塞
 * {@code ColoredRectangleRenderState}，{@code GuiGraphicsExtractor.java:219-223}；
 * {@code text} 塞 {@code GuiTextRenderState}，{@code :250-253}），真正交给 GPU 是后面那一遍。
 * 而 {@code BrowserScreen.drawBrowser} 现在是「当场绑着色器与纹理、当场把四个顶点提交出去」——
 * 在这种模型里坏掉的不是编译，是【顺序】：那一个四边形会在本帧其余界面内容之前直接怼上帧缓冲，
 * 而且不受 render-state 那套 scissor / stratum 管。
 *
 * <p>正解是让浏览器那张纹理以 {@code GpuTextureView} 的身份交给画布，走
 * {@code blit(GpuTextureView, GpuSampler, x0, y0, x1, y1, u0, u1, v0, v1)}
 * （{@code GuiGraphicsExtractor.java:374}），本层就不再自己提交顶点。那是<b>浏览器那一族</b>的活，
 * 还得等 MCEF 出一份 26.x 的构建（现在 {@code com.cinemod.mcef} 整个包不存在）。
 *
 * <p>所以这里<b>宁可让它报「找不到符号 方法 texturedQuad」而编译失败</b>，也不塞一个静默的 no-op：
 * 后者会让浏览器界面在 26.3 上变成一块白板，而编译、十道闸、断言测试【都不会说话】。
 */
public final class Draw {

    private Draw() {}

    /**
     * 画屏幕背景。
     *
     * <p>这一支上它叫 {@code Screen#extractBackground}（{@code Screen.java:393}），收的还是
     * 【画布 + 鼠标 + partialTick】那四个：1.21.1 那个
     * {@code renderBackground(GuiGraphics, int, int, float)} 在这支换了名字，方法体还是
     * 那三件事 —— 游戏内 UI 走透明底、没有世界就画全景、其余画模糊与暗底。
     */
    public static void screenBackground(Screen screen, GuiGraphicsExtractor g,
                                        int mouseX, int mouseY, float partialTick) {
        screen.extractBackground(g, mouseX, mouseY, partialTick);
    }

    /**
     * 裁剪框有没有被人漏下来 —— 问法与 1.21.1 那一支相同：问 {@code containsPointInScissor(0, 0)}。
     *
     * <p>26.x 的 {@code ScissorStack}（{@code GuiGraphicsExtractor.java:1431}）保留了那两条性质：
     * 「栈空恒为 true」（{@code containsPoint} 第一句就是 {@code stack.isEmpty() ? true : ...}，
     * {@code :1457-1458}）与「弹空再弹要抛 {@code Scissor stack underflow}」（{@code :1445-1451}）。
     * 所以这一支上同样是：漏下来的框恰好包含窗口左上角时<b>发现不了</b>，而拿它当循环条件弹不穿。
     * 构造时那个 {@code ScreenRectangle(0, 0, guiWidth, guiHeight)}（{@code :116}）是
     * {@code push} 求交用的<b>基准</b>，没有压在栈上，所以栈还是从空开始。
     *
     * <p>与 1.20.1 那一支不同 —— 那边问 GL，不会漏判。
     */
    public static boolean scissorLeaked(GuiGraphicsExtractor g) {
        return !g.containsPointInScissor(0, 0);
    }

    /**
     * 画一张贴图（或它的一块），带一个【整体不透明度】。共用代码里所有画贴图的动作都从这里过。
     *
     * <h2>那一串 GL 状态全没了，而且不该补回来</h2>
     *
     * 老三支要自己 {@code enableBlend / defaultBlendFunc / disableBlend}，是因为那条
     * {@code blit(ResourceLocation, ...)} 从头到尾不碰混合状态，半透明贴图于是被当成不透明画。
     * 这一支上混合写在 pipeline 里：{@code RenderPipelines.GUI_TEXTURED_SNIPPET}
     * （{@code RenderPipelines.java:318-327}）带着
     * {@code ColorTargetState(BlendFunction.TRANSLUCENT)}，谁走这条 pipeline 谁就混合，
     * 不再取决于【轮到这一句时 GL 恰好是什么状态】。而且 {@code RenderSystem} 上那三个方法
     * 在这一支【已经不存在了】（本步之前 {@code GuiUtil} 那三句就是三条「找不到符号」）。
     *
     * <h2>颜色从【全局】搬到了【这一次绘制】</h2>
     *
     * {@code blit(RenderPipeline, Identifier, int x, int y, float u, float v, int width, int height,
     * int srcWidth, int srcHeight, int textureWidth, int textureHeight, int color)}
     * （{@code GuiGraphicsExtractor.java:340-368}）就是老那条 11 参 blit 的对应物，多的正是最后
     * 那个 color；而 {@code ARGB.white(float alpha)}（{@code ARGB.java:294-296}）
     * = {@code as8BitChannel(alpha) << 24 | 0xFFFFFF}，等价于老那句
     * {@code setColor(1.0F, 1.0F, 1.0F, alpha)}。alpha 为 1 时它是 {@code -1}，
     * 而原版自己那条不带色的 blit 传的就是 {@code -1}（{@code :337}）—— 所以整张路走下来，
     * 100% 不透明的东西与从前【逐像素同一个值】，不是"差不多"。
     *
     * <h2>代价：这一支上 alpha 要过一遍 8 位量化</h2>
     *
     * {@code as8BitChannel} 是 {@code floor(v * 255)}，而 {@code setShaderColor} 是浮点直乘，
     * 于是半透明贴图的每一档 alpha 可能比从前浅/深 1/255（0.35 这一档落到 89/255）。
     * 这是【粒度】的差，不是【方向】的错，而且没有别的入口：这一支的顶点格式就是
     * {@code POSITION_TEX_COLOR}，颜色只有 8 位这一条路。
     *
     * <h2>但【越过 1.0 的提亮】这一支表达不出来</h2>
     *
     * 顶点色是四条 8 位通道：{@code ARGB.white(float)}（{@code ARGB.java:294-296}）与
     * {@code colorFromFloat}（{@code :315-317}）都走 {@code as8BitChannel}（{@code :343-345}），而那句是
     * {@code Mth.floor(value * 255.0F)}，【不夹范围】（{@code Mth.floor} = {@code (int)Math.floor(v)}，
     * {@code Mth.java:62-64}）。老那句全局 {@code setShaderColor} 是浮点，可以大于 1，而
     * {@code PhoneTheme.SKIN_HOVER_BRIGHTNESS = 1.8f} 要的正是越过 1.0：1.8 在这里先变成 459，
     * 459 << 24 裁成 int 只剩 {@code 0xCB} 当 alpha，也就是 0.796。所以照老句直译过来
     * 不是【提亮没了】，是【提亮变成变暗加半透明】—— 那更是一种静默的行为丢失，所以悬停提亮那两处
     * （{@code PhoneSkin.drawOrFill} 的 highlight 与 {@code PhoneChassis} 的导航键）
     * 【仍然留在 {@code g.setColor(...)} 上继续报编译错误】，等定夺：这一支上唯一像是真办法是
     * 再叠一遍带 {@code BlendFunction.ADDITIVE} 的 textured pipeline
     * （{@code RenderPipelines.GUI_NAUSEA_OVERLAY} 就是 GUI_TEXTURED_SNIPPET + ADDITIVE，
     * {@code RenderPipelines.java:1126-1131}），但那与"着色器颜色乘 1.8"在半透明像素上并不等价，
     * 得先定下来要不要。取证与取舍记在 {@code docs/PORTING-26.3.md} 的 §三十九。
     */
    public static void textured(GuiGraphicsExtractor g, Identifier tex,
                                int x, int y, int w, int h,
                                float u, float v, int srcW, int srcH, int texW, int texH,
                                float alpha) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, srcW, srcH, texW, texH,
                ARGB.white(alpha));
    }
}
