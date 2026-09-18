package com.november.mcphone.core.script.server.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 货币存档的原子快照：{@code <世界>/mcphone/economy/economy.dat}。
 *
 * <p>Forge 1.20.1 与 Fabric（原版）写 SavedData 是就地截断重写，写到一半被杀，那一份就坏了、上一份也没了；
 * 这一份写临时文件、fsync、再原子改名 —— 任何时刻去读，拿到的要么是完整的上一份，要么是完整的这一份。
 * <b>由 {@link EconomyData#write} 在世界保存时、主线程上写，与 SavedData 那份是同一个 tag</b>，不是另一份各自序列化的状态。
 *
 * <p>格式是 gzip 包着的 NBT。gzip 自带 CRC：读到尾巴才校验，所以 {@link #read} 会把流读完。
 */
final class EconomySnapshot {

    private EconomySnapshot() {
    }

    static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("mcphone").resolve("economy").resolve("economy.dat");
    }

    /** 整份替换。失败时抛，目标文件一个字节都没动。 */
    static void write(Path file, CompoundTag tag) throws IOException {
        write(file, out -> NbtIo.write(tag, out));
    }

    /** 往流里写内容的那一段。拆出来是为了测得到"写到一半出错"。 */
    @FunctionalInterface
    interface Body {
        void writeTo(DataOutputStream out) throws IOException;
    }

    static void write(Path file, Body body) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            GZIPOutputStream gz = new GZIPOutputStream(Channels.newOutputStream(ch));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(gz));
            body.writeTo(out);
            out.flush();
            gz.finish();
            // 改名之前内容先落到盘上：否则掉电后改名生效了、内容却没有，读到的是一份空文件
            ch.force(true);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // 临时文件就在旁边，同一个文件系统，正常到不了这里
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 读不出来（没有、截断、CRC 不对、不是 NBT）一律抛。 */
    static CompoundTag read(Path file) throws IOException {
        try (InputStream raw = Files.newInputStream(file);
             GZIPInputStream gz = new GZIPInputStream(new BufferedInputStream(raw))) {
            CompoundTag tag = NbtIo.read(new DataInputStream(gz));
            // 读到流尾，gzip 才会核对 CRC 与长度
            byte[] rest = new byte[256];
            while (gz.read(rest) != -1) {
                // 把尾巴读完
            }
            return tag;
        } catch (RuntimeException e) {
            throw new IOException("快照读不出来：" + e, e);
        }
    }
}
