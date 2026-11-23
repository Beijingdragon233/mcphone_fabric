package com.november.mcphone.feature.chat.client.favorite;

import com.november.mcphone.util.Java8;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import com.november.mcphone.core.PhonePaths;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.UUID;

/** 运行时仅负责定位本机收藏文件：账号、服务器地址或单人世界目录各自隔离。 */
public final class ChatFavorites {
    private ChatFavorites() {}
    private static Path active;
    private static java.util.concurrent.CompletableFuture<FavoriteStore> opening;
    // 同一路径只有一个写入者；快速切服返回时仍复用正在保存的实例。
    private static final java.util.Map<Path,java.util.concurrent.CompletableFuture<FavoriteStore>> STORES=
            new java.util.LinkedHashMap<>(8,0.75f,true);
    public static Path path(Path directory,UUID account,String server) {
        try {
            String key=Java8.formatHex(MessageDigest.getInstance("SHA-256").digest(server.getBytes(StandardCharsets.UTF_8)));
            return PhonePaths.FAVORITES.in(directory).resolve(account.toString()).resolve(key+".json");
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static FavoriteStore current() {
        net.minecraft.client.Minecraft mc=Minecraft.getInstance();
        if (mc.player==null || mc.getConnection()==null) throw new IllegalStateException("收藏需要有效玩家会话");
        net.minecraft.client.server.IntegratedServer server=mc.getSingleplayerServer();net.minecraft.client.multiplayer.ServerData remote=mc.getCurrentServer();
        String scope=server!=null?"local:"+server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                :remote!=null?"remote:"+remote.ip.toLowerCase(java.util.Locale.ROOT):null;
        if (scope==null) throw new IllegalStateException("无法识别当前服务器");
        Path file=path(mc.gameDirectory.toPath(),mc.player.getUUID(),scope);
        if (!file.equals(active)) {
            active=file;
            opening=STORES.computeIfAbsent(file,p->java.util.concurrent.CompletableFuture.supplyAsync(
                    ()->new FavoriteStore(p),net.minecraft.Util.backgroundExecutor()));
            Iterator<Entry<Path,CompletableFuture<FavoriteStore>>> iterator=STORES.entrySet().iterator();
            while(STORES.size()>8 && iterator.hasNext()) {
                Entry<Path,CompletableFuture<FavoriteStore>> old=iterator.next();
                if(!old.getKey().equals(active) && old.getValue().isDone() && !old.getValue().join().busy()) iterator.remove();
            }
        }
        return opening.isDone()?opening.join():null;
    }
    public static void clear() { active=null;opening=null; }
}
