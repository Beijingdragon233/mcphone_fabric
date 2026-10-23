package com.november.mcphone.platform.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastComponent;

/**
 * 把「通知区那个管理器从哪儿拿」收在一层。
 *
 * <h2>为什么这一层不能省</h2>
 *
 * 这一支上它就是 {@code Minecraft} 上的一个 getter。26.x 那边这个 getter 没了，
 * 管理器挪进了 {@code Gui}，取法变成「先取 gui，再调一个方法」，那个类本身也跟着改了名。
 * 而 {@code versions/name-renames.json} 三族规则都是「标识符 → 标识符」，
 * 写不出【中间多一段 . 再一个名字、末尾多一对括号】这种形变 —— 判据与 {@link Screens} 同一条（§二十八）。
 *
 * <p>至于【改名】那一半为什么也没进那张表：这一层把类型挡住了，共用代码拿 {@code var} 接住，
 * 于是老名字在共用代码里一次都不出现。那张表有一道闸专门拦「一次都没命中的规则」
 * （空转的规则比没有规则更糟），硬加一条只会给自己埋雷。
 *
 * <p>这一层只管【拿管理器】。往里加通知、按 token 找已在显示或还在排队的那一条
 * （{@code addToast} / {@code getToast}）四支同形，留在共用代码里，不外移 ——
 * 那是「通知怎么合并」，是业务；这里只管「那个对象叫什么、在哪儿」。
 */
public final class Toasts {

    private Toasts() {}

    /**
     * 原版那个通知区管理器。
     *
     * <p>返回类型两支不同名，所以【调用方别写类型名】：共用代码里一律
     * {@code var toasts = Toasts.manager(mc);}，写了就把这一层白建了。
     */
    public static ToastComponent manager(Minecraft mc) {
        return mc.getToasts();
    }
}
