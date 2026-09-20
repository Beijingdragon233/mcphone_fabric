package com.november.mcphone.platform.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 把「此刻屏幕上开着哪个界面」这一件事收在一层。
 *
 * <h2>为什么这一层不能省</h2>
 *
 * 这一支上它是 {@code Minecraft} 的一个 public 字段 {@code screen}，读它就行。
 * 26.x 把那个字段整个搬进了 {@code Gui}，{@code Minecraft#screen} 不复存在，
 * 读法换成 {@code minecraft.gui.screen()}。名字没丢、东西挪了家，
 * 而 {@code versions/name-renames.json} 那三族规则都是「标识符 → 标识符」，
 * 写不出「中间多一段 {@code .gui}、末尾多一对括号」这种形变 —— 所以只能建接缝。
 *
 * <p>这一层只管【读】。开界面那条路（老 {@code Minecraft#setScreen(Screen)} 对
 * 26.x {@code Minecraft#setScreenAndShow(Screen)}）不在这里，那是真改名，
 * 走改名表的 methodRenames。
 */
public final class Screens {

    private Screens() {}

    /**
     * 此刻挂在屏幕上的那个界面，没开界面就是 {@code null}。
     *
     * <p>就是原版那个字段，读它没有副作用；{@code mc} 用调用方手里那一份，
     * 这一层不自己去 {@code getInstance()}，为的是共用代码里那些已经握着实例的
     * 调用点不必再多拿一次。
     */
    public static Screen current(Minecraft mc) {
        return mc.screen;
    }
}
