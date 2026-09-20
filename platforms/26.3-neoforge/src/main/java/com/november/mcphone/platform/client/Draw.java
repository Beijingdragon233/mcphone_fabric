package com.november.mcphone.platform.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

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
}
