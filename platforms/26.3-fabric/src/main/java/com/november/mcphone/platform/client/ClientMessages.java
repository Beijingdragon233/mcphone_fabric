package com.november.mcphone.platform.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 把「给某个玩家看一眼即时反馈」这一件事收在一层。
 *
 * <h2>为什么这一层不能省</h2>
 *
 * {@code displayClientMessage(Component, boolean)} 在 26.3 上【整个不存在】：
 * {@code Entity.java}、{@code Player.java}、{@code LocalPlayer.java}、{@code ServerPlayer.java}、
 * {@code ChatListener.java} 里都搜不到这个名字。它不是改名，是这条路按两侧拆开了。
 *
 * <h2>这一句本来就有【两侧】，接缝必须两条都接</h2>
 *
 * 老那一支 {@code Player#displayClientMessage} 是空方法体，但两个子类各自覆写过：
 *
 * <ul>
 *   <li><b>服务端侧</b>：{@code ServerPlayer} 覆写成 {@code sendSystemMessage(msg, actionBar)}，
 *       也就是发一个 {@code ClientboundSystemChatPacket} 给那个玩家。本仓大部分调用点走的是这一条
 *       （{@code NetworkHandler}、{@code StoreNetworking}、{@code ChatNetworking}、
 *       {@code MusicNetworking}、{@code NotesNetworking}、{@code TerminalOpener}、
 *       {@code TeleportService} 加起来 10 处，接收者都是 {@code ServerPlayer}）。</li>
 *   <li><b>客户端侧</b>：{@code LocalPlayer} 覆写成
 *       {@code chatListener().handleSystemMessage(msg, actionBar)}，直接往本地界面上画。</li>
 * </ul>
 *
 * <p>26.3 上 {@code ServerPlayer} 那半【原样还在】—— {@code sendSystemMessage(Component, boolean overlay)}
 * 还在、第二参数还叫 {@code overlay}、还发同一个包（{@code ServerPlayer.java:1927}）。
 * 没了的只有 {@code LocalPlayer} 那一侧的入口。所以这一层【两条都要写】：
 * 只写客户端那条会把 9 处服务端提示静默吞掉，而且四支都编得过、没有一道闸会响。
 *
 * <h2>{@code LocalPlayer} 那一侧为什么不能拿 {@code isPresent()} 式的直觉去替</h2>
 *
 * 26.x 把 {@code handleSystemMessage} 的第二个参数从 {@code isOverlay} 换成了 {@code remote}：
 * {@code true} 走 {@code addServerSystemMessage} 并【多落一条日志】，{@code false} 走
 * {@code addClientSystemMessage} 且【不记日志】。老那句非动作栏那一支是 {@code addMessage}
 * 加一条 {@code logSystemMessage}，所以这里传 {@code true}。为的是对上行为，不是为了语义好听。
 *
 * <p>动作栏那一支对应 {@code handleOverlay(msg)}（{@code gui.hud.setOverlayMessage} 加旁白）。
 * 26.x 的 {@code handleSystemMessage} 外面还多套了 {@code chatAbilities().canReceiveSystemMessages()}
 * 与 {@code isFriendOnlyRestricted(uuid)} 两道闸，只会让消息【少显示】不会多显示；
 * 那正是游戏自己处理系统消息走的路，跟着它走，不自创一条绕过社交过滤的路。
 *
 * <h2>第三种玩家：什么都不发生</h2>
 *
 * {@code RemoteClientPlayer}（单机存档里客户端侧的"别的玩家"）拿 {@code Player} 那份空方法体，
 * 本来什么都不发生。这一层保持一致：两个分支都不落就什么都不做。
 * 顺带一道防线：只有真拿到 {@code LocalPlayer} 才去碰 {@code Minecraft.getInstance()}，
 * 专用服上那玩意儿根本取不到，而 {@code RuntimeDistCleaner} 一加载碰了客户端类型的类就抛。
 */
public final class ClientMessages {

    private ClientMessages() {}

    /**
     * 给这个玩家看一眼这条消息。{@code actionBar} 为真走物品栏上方那一行，否则走聊天栏。
     *
     * <p>服务端来的玩家走封包，客户端本地玩家走 {@code chatListener()}，
     * 两者与 1.20.1 / 1.21.1 上 {@code ServerPlayer} 与 {@code LocalPlayer} 各自的 override 一一对应。
     */
    public static void show(Player p, Component msg, boolean actionBar) {
        if (p instanceof ServerPlayer sp) {
            sp.sendSystemMessage(msg, actionBar);
        } else if (p instanceof LocalPlayer) {
            ChatListener listener = Minecraft.getInstance().gui.chatListener();
            if (actionBar) {
                listener.handleOverlay(msg);
            } else {
                listener.handleSystemMessage(msg, true);
            }
        }
    }
}
