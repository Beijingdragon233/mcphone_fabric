package com.november.mcphone.platform.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * 把「给某个玩家看一眼即时反馈」这一件事收在一层。
 *
 * <h2>为什么这一层不能省</h2>
 *
 * {@code displayClientMessage(Component, boolean)} 在 26.3 上【整个不存在】：
 * {@code Entity}、{@code Player}、{@code LocalPlayer}、{@code ServerPlayer}、{@code ChatListener}
 * 里都搜不到这个名字。它不是改名，是这条路按两侧拆开了 —— 所以这一层也分两侧。
 *
 * <h2>这一句本来就有两侧，两支都得接住</h2>
 *
 * {@code Player#displayClientMessage} 自己是空方法体，真身在两个子类的 override 里：
 * {@code ServerPlayer} 那侧是 {@code sendSystemMessage(msg, actionBar)}，发一个
 * {@code ClientboundSystemChatPacket} 给那个玩家；{@code LocalPlayer} 那侧是
 * {@code chatListener().handleSystemMessage(msg, actionBar)}，直接往本地界面上画。
 * 本仓 14 个调用点里 10 处走的是服务端那侧（`{@code TeleportService}`、{@code TerminalOpener} 两处、
 * {@code NetworkHandler} 两处、{@code ChatNetworking}、{@code MusicNetworking}、
 * {@code NotesNetworking}、{@code StoreNetworking} 两处），4 处走客户端那侧
 * （{@code PhoneScreen}、{@code ChatConversation}、{@code ChatImageSender}、{@code CameraHandler}）。
 *
 * <p>这一支上两侧都还在，一个方法体就够：直接递给原版那句，谁覆写过什么由原版决定。
 * 26.x 那一支要拆成 {@code ServerPlayer.sendSystemMessage} 与
 * {@code Gui.chatListener()} 两条，而且第二参数的意思换了，两份【不是同义改写】，别照着改。
 *
 * <p>第三种玩家：{@code RemoteClientPlayer} 拿的是 {@code Player} 那份空方法体，
 * 本来什么都不发生，两支在这点上也是一致的。
 */
public final class ClientMessages {

    private ClientMessages() {}

    /**
     * 给这个玩家看一眼这条消息。{@code actionBar} 为真走物品栏上方那一行，否则走聊天栏。
     * 服务端来的玩家走封包，客户端本地玩家走界面，见类注释。
     */
    public static void show(Player p, Component msg, boolean actionBar) {
        p.displayClientMessage(msg, actionBar);
    }
}
