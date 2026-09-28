package com.november.mcphone.api.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

/**
 * 原版的多行输入框。
 *
 * <h2>这一支为什么还要留这么一个类</h2>
 *
 * 另三支上它是一个【补丁】：原版控件在自己 {@code renderWidget} 的正中间调那对
 * {@code enableScissor}/{@code disableScissor}，交上去的是控件自己的 x/y/宽高，而老原版的
 * 那两句【完全不看变换矩阵】；手机整台是套在一层 pose 缩放里画的（{@code PhoneScreen} 绕手机
 * 中心 scale），于是内容缩放了、裁剪框没缩，放大以后正文顶上几行整行不见。补丁只能整个覆写
 * 那个方法，理由与症状记在 1.20.1 / 1.21.1 的同名文件里，起于 1.10.4（API 代号 2）。
 *
 * <p>26.x 把这件事【在上游做掉了】：{@code GuiGraphicsExtractor#enableScissor}
 * （该类 {@code :151-153}）先把矩形过一遍 {@code transformAxisAligned(this.pose)} 再压栈。
 * 父类 {@code AbstractTextAreaWidget#extractWidgetRenderState}（{@code :64-81}）用的正是这一句。
 * 所以这一支【一个方法都不覆写】——再覆写一遍就是把变换做两次。
 *
 * <h2>剩下的只有一件事：构造入口</h2>
 *
 * 26.3 的 {@link MultiLineEditBox} 公开的造法是 {@code MultiLineEditBox.builder()}
 * （{@code :276}）＋ {@code Builder#build}（{@code :330}），底下真正干活的 12 参构造器是
 * {@code private}（{@code :35}）。Java 的子类构造器只能调直接父类的构造器，静态工厂递不进
 * {@code super(...)}，而附属模组拿到的 API 是一个【构造器】——那一句 {@code new} 不能改。
 * 于是这一支用 access transformer 把那个构造器放宽到 {@code protected}：
 * 文件在 {@code src/main/resources/META-INF/accesstransformer.cfg}（编译期由
 * {@code build.gradle} 的 {@code neoForge.accessTransformers} 吃进去，运行期靠加载器
 * 对同一个路径的回落），机制与许可的说明在 {@code gradle/access-transformer-LICENSE.md}，
 * 取证与来回比较在 {@code docs/PORTING-26.3.md} 的 §三十七。
 *
 * <h2>补上去的那五个参数是不是老三支的行为</h2>
 *
 * 是，而且不是我挑的数：它们就是 26.3 那个 {@code Builder} 的字段初值（{@code :283-288}），
 * 逐个对得上 1.21.1 里写死的那几个：
 * <ul>
 *   <li>{@code textColor = -2039584} = 1.21.1 的 {@code TEXT_COLOR}（{@code MultiLineEditBox.java:21}）</li>
 *   <li>{@code textShadow = true} = 1.21.1 调的是 {@code GuiGraphics.drawString(font, text, x, y, color)}
 *       那个五参重载，它内部转的就是带阴影的六参那一个（{@code GuiGraphics.java:556-558}）</li>
 *   <li>{@code cursorColor = -3092272} = 1.21.1 的 {@code CURSOR_INSERT_COLOR}（{@code :19}）</li>
 *   <li>{@code showBackground}/{@code showDecorations} = true = 1.21.1 的父类无条件画背景与装饰</li>
 * </ul>
 *
 * <h2>{@code placeholder} 与 {@code message} 各落在哪个位置</h2>
 *
 * 老三支 {@code super(font, x, y, width, height, placeholder, message)} 里 {@code message}
 * 走的是父类的 narration（1.21.1 {@code :30} 那句 {@code super(x, y, width, height, message)}）。
 * 26.3 那个构造器第 6、7 个参数依次是 {@code placeholder}、{@code narration}，所以按位置原样
 * 递过去就是同一件事，没有换顺序。
 *
 * <h2>同一个坑在【页面级裁剪】那一条路上：已经收进 seam，本类不管它</h2>
 *
 * shared 的 {@code GuiUtil.enableScissor} 自己先把矩形按 pose 换算一遍、再交出去；而这一支的
 * 原版那句【也】换算一遍。所以「交出去」那一步收进了 {@code Transforms.scissor}：这一支在那儿
 * 压一层单位矩阵再弹掉，让原版的变换等于不动，四支于是共用同一套换算与取整规矩。
 * 取证与来回比较在 {@code docs/PORTING-26.3.md} 的 §三十八；对外的 {@code PhoneCanvas.clipped}
 * 走的同一条路，进游戏复核时一起看。
 *
 * <p>类是 {@code final} 的，和另三支一致：这份镜像得跟着原版走，留继承口子等于把镜像变成
 * 不能改的 API。要改行为就在外面包一层。
 */
public final class PhoneMultiLineEditBox extends MultiLineEditBox {

    /** 26.3 的 {@code Builder.textColor} 初值，等于 1.21.1 里写死的 {@code TEXT_COLOR}。 */
    private static final int DEFAULT_TEXT_COLOR = -2039584;

    /** 26.3 的 {@code Builder.cursorColor} 初值，等于 1.21.1 里写死的 {@code CURSOR_INSERT_COLOR}。 */
    private static final int DEFAULT_CURSOR_COLOR = -3092272;

    public PhoneMultiLineEditBox(Font font, int x, int y, int width, int height,
                                 Component placeholder, Component message) {
        // 后五个就是 Builder 的默认值；narration 收的是老三支那个 message。
        super(font, x, y, width, height, placeholder, message,
                DEFAULT_TEXT_COLOR, true, DEFAULT_CURSOR_COLOR, true, true);
    }
}
