package com.november.mcphone.platform;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;

/**
 * 把「报一个人的名字」这一件事收在一层。
 *
 * <h2>为什么这一层不能省</h2>
 *
 * 26.x 上两处同时动了：
 *
 * <ul>
 *   <li>{@code GameProfile}（authlib）换成了带访问器记录的样子，取名从 {@code getName()} 变成 {@code name()}；</li>
 *   <li>{@code MinecraftServer#getProfileCache()} 与 {@code net.minecraft.server.players.GameProfileCache}
 *       整个没了，那本档案换成了 {@code server.services().nameToIdCache()}（{@code UserNameToIdResolver}）。</li>
 * </ul>
 *
 * <h2>【只查档案，不联网】是这一层的核心，不是细节</h2>
 *
 * 老那句 {@code getProfileCache().get(id)} 读的是内存里那份 {@code usercache.json} 缓存，
 * <b>未命中就返回空，绝不发请求</b>。26.x 上看着更贴近的
 * {@code services().profileResolver().fetchById(id)}【会】联网：
 * {@code ProfileResolver.Cached} 那个 {@code LoadingCache} 未命中就直接问 session 服务
 * （{@code ProfileResolver.java} 里 {@code profileCacheById} 的 {@code CacheLoader}）。
 * 拿它替老那句，等于在服务器主线程上凭空多出一次 HTTP，而离线模式、私服、断网的机器上
 * 只会白等一场。所以这一层对的是 {@code nameToIdCache().get(id)}：
 * 它同样只读 {@code usercache.json} 那份内存表（{@code CachedUserNameToIdResolver.java:135-143}，
 * 命中就返回，未命中直接空），跟老那支是同一件事。
 *
 * <p>调用方要的都只是"这个人叫什么 / 档案里有没有这个人"，
 * 所以这一层往外递的是 {@code String}，不去拼一个 {@code GameProfile} 回来。
 * 从没上过线的人查不到，那是事实，这里也不替调用方编一个名字出来。
 */
public final class Profiles {

    private Profiles() {}

    /** 这个档案上的名字。 */
    public static String name(GameProfile p) {
        return p.name();
    }

    /** 档案缓存（{@code usercache.json}）里这个人叫什么；没记录就是空。不联网，见类注释。 */
    public static Optional<String> cachedName(MinecraftServer server, UUID id) {
        return server.services().nameToIdCache().get(id).map(NameAndId::name);
    }
}
