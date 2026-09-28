package com.november.mcphone.platform.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 容器界面（末影箱、唱片仓、终端卡槽）的每支一份基类 —— 把 26.x 换掉的【那几个渲染入口】
 * 挡在共用代码之外，让子类在四支上都覆写同一组名字。
 *
 * <h2>为什么必须有这一层</h2>
 *
 * 与 {@code PhoneScreenBase} 同一个道理：这些是【覆写】，不是调用。门面改得了「怎么调一个
 * 方法」，改不了「一个方法被谁覆写」—— 子类照 1.21.1 的名字写 {@code render}，在 26.x 上
 * 不会编译报错（那一支根本没有这个方法名），只会<b>永远不被调到</b>：界面整个不画。
 *
 * <h2>26.x 换掉的三件事</h2>
 *
 * <ul>
 *   <li>{@code render(GuiGraphics,int,int,float)} → {@code extractRenderState(GuiGraphicsExtractor,...)}
 *       （原版容器那份在 {@code AbstractContainerScreen.java:91}）。</li>
 *   <li>{@code renderLabels} → {@code extractLabels}（{@code :194}），
 *       {@code renderTooltip} → {@code extractTooltip}（{@code :169}）。</li>
 *   <li><b>{@code renderBg} 整个没了</b> —— 原版这一支的 {@code AbstractContainerScreen}
 *       不再画背板（{@code BACKGROUND_TEXTURE_WIDTH}/{@code INVENTORY_LOCATION} 只剩常量，
 *       全类搜不到一次绘制），背板成了<b>子类自己的事</b>：{@code InventoryScreen}
 *       自己画自己那张。所以这一层把那个 hook 【补回来】，位置照 1.21.1 的链路放。</li>
 * </ul>
 *
 * <h2>背板挂在 {@code extractBackground} —— 这一支原版自己也是这么挂的</h2>
 *
 * 不是这一层自创的位置：26.x 的 {@code InventoryScreen} 就是这样画自己那张面板的 ——
 * 它覆写 {@code extractBackground}（{@code InventoryScreen.java:99-103}），先
 * {@code super.extractBackground(...)}，再 {@code graphics.blit(RenderPipelines.GUI_TEXTURED,
 * INVENTORY_LOCATION, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256)}。 那一层 hook 在
 * 这一支没有了，画背板是【子类自己的事】。
 *
 * 1.21.1 上 {@code renderBg} 是被 {@code AbstractContainerScreen#renderBackground} 叫起来的
 * （{@code renderBackground = renderTransparentBackground + renderBg}），而后者被 {@code render}
 * 叫。这一支的对应链路是 {@code Screen#extractRenderStateWithTooltipAndSubtitles}
 * （final，{@code Screen.java:108-118}）：先 {@code nextStratum()}、再
 * {@code extractBackground}（{@code :110}）、发 {@code ScreenEvent.Render.Background}、
 * 再 {@code nextStratum()}、然后才是 {@code extractRenderState}（{@code :113}）。
 * 所以下面那个 {@code extractBackground} 覆写 = 「透明底 + 我们的背板」，与 1.21.1 那对
 * {@code renderBackground} 逐句同形（它也不叫 super，走的也是透明底那一支）。
 *
 * <p>于是每帧的抽取次数与 1.21.1 一致：子类自己那句 {@code Draw.screenBackground} 一次、
 * 原版入口一次，背板各跟着画两次 —— 那是<b>现支本来的行为</b>，本轮只求对上，不求顺手改。
 */
public abstract class PhoneContainerScreenBase<T extends AbstractContainerMenu>
        extends AbstractContainerScreen<T> {

    /**
     * 尺寸走构造。这一支上 {@code imageWidth}/{@code imageHeight} 是
     * {@code protected final}（{@code AbstractContainerScreen.java:38-39}），
     * 只能从那个五参构造进去（{@code :64}）；老那三支是「先 super 再赋值」，
     * 差别就留在各自那一份里。
     */
    protected PhoneContainerScreenBase(T menu, Inventory playerInventory, Component title,
                                       int imageWidth, int imageHeight) {
        super(menu, playerInventory, title, imageWidth, imageHeight);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.render(g, mouseX, mouseY, partialTick);
    }

    /** 共用代码覆写的名字。默认实现就是把原版那一套交回去。 */
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(g);
        this.renderBg(g, partialTick, mouseX, mouseY);
    }

    /**
     * 画背板。这一支上原版没有这个方法，是这一层补出来的 hook —— 默认什么都不画，
     * 与「这一支的 {@code AbstractContainerScreen} 自己不画背板」一致。
     */
    protected void renderBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {}

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        this.renderLabels(g, mouseX, mouseY);
    }

    /** 标题与「物品栏」那两行字。默认走原版（它只画这两行，见 {@code :194-197}）。 */
    protected void renderLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
    }

    @Override
    public void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        this.renderTooltip(g, mouseX, mouseY);
    }

    /** 光标下物品的提示。共用代码里那句 {@code renderTooltip(g, mx, my)} 就落在这。 */
    public void renderTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
    }
}
