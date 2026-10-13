package com.november.mcphone.feature.chat.client.favorite;

import com.november.mcphone.core.client.AtomicWrite;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 写入成功后才替换内存快照；损坏文件只读锁定，保留用户原文件供恢复。 */
public final class FavoriteStore {
    public static final int MAX_ENTRIES=512, MAX_BYTES=2*1024*1024;
    public enum Result { OK, EXISTS, LIMIT, STORAGE_ERROR }
    @FunctionalInterface public interface Writer { void write(Path path,String json) throws IOException; }
    private final Path path;
    private final Writer writer;
    private Map<String,FavoriteText> values=new LinkedHashMap<>();
    private boolean available=true;
    public FavoriteStore(Path path) { this(path,(file,json)->AtomicWrite.replace(file,w->w.write(json))); }
    public FavoriteStore(Path path,Writer writer) { this.path=path;this.writer=writer;load(); }
    public boolean available() { return available; }
    public List<FavoriteText> snapshot() {
        var all=new ArrayList<>(values.values()); java.util.Collections.reverse(all);return List.copyOf(all);
    }
    public Result add(FavoriteText entry) {
        if (!available) return Result.STORAGE_ERROR;
        if (values.containsKey(entry.key())) return Result.EXISTS;
        if (values.size()>=MAX_ENTRIES) return Result.LIMIT;
        var next=new LinkedHashMap<>(values);next.put(entry.key(),entry);return commit(next);
    }
    public Result remove(String key) {
        if (!available) return Result.STORAGE_ERROR;
        if (!values.containsKey(key)) return Result.OK;
        var next=new LinkedHashMap<>(values);next.remove(key);return commit(next);
    }
    private Result commit(Map<String,FavoriteText> next) {
        String json=FavoriteCodec.encode(next);
        if (json.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES) return Result.LIMIT;
        try { writer.write(path,json);values=next;return Result.OK; }
        catch (IOException|RuntimeException e) {
            com.november.mcphone.MCphone.LOGGER.warn("收藏写入失败 [{}]，保留原快照",path,e);
            return Result.STORAGE_ERROR;
        }
    }
    private void load() {
        try {
            if (!Files.exists(path)) return;
            // 即使文件在检查大小后被其他进程替换，也最多读取上限加一字节。
            byte[] bytes;
            try (var input=Files.newInputStream(path)) { bytes=input.readNBytes(MAX_BYTES+1); }
            if (bytes.length>MAX_BYTES) throw new IOException("收藏文件超限");
            String json=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            values=FavoriteCodec.decode(json);
        } catch (IOException|RuntimeException e) {
            available=false;
            com.november.mcphone.MCphone.LOGGER.warn("收藏读取失败 [{}]，保留原文件并锁定写入",path,e);
        }
    }
}
