package com.november.mcphone.platform;

import java.util.Set;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * {@code CompoundTag} 上「先看类型、再取值」这一对 —— 26.x 这一支。
 *
 * <h2>为什么要这么一层</h2>
 *
 * 存档校验要的是【严格】：键在、而且类型正好是它。1.20.1 / 1.21.1 上这句话写作两句 ——
 * {@code contains(key, Tag.TAG_LONG)} 判型，{@code getLong(key)} 取值。
 * 26.x 把取值那半全换成了 {@code Optional}：对着 26.3 的源，
 * {@code getLong} 是 {@code Optional<Long>}、{@code getInt} 是 {@code Optional<Integer>}、
 * {@code getString} 是 {@code Optional<String>}、{@code getBoolean} 是
 * {@code Optional<Boolean>}、{@code getCompound} 是 {@code Optional<CompoundTag>}、
 * {@code getList} 只剩一参数版本返 {@code Optional<ListTag>}，而 {@code getAllKeys()}
 * 改叫 {@code keySet()}。两句都写不了了，所以收在这一层。
 *
 * <h2>为什么不能直接拿 {@code isPresent()} 当判型</h2>
 *
 * 看着最顺的替法是 {@code t.getLong(k).isPresent()}，这条【错的】：
 *
 * <ul>
 *   <li>{@code getLong} 的实现是 {@code getOptional(name).flatMap(Tag::asLong)}，
 *       而 {@code NumericTag.asLong()} 对六种数值标签【一律给得出数】——
 *       一张 {@code IntTag} 会给出非空。老的 {@code contains(key, Tag.TAG_LONG)}
 *       在 1.21.1 上是 {@code getTagType(key) == 4}，{@code IntTag} 直接不算。</li>
 *   <li>{@code getString} 更松：{@code StringTag.asString()} 之外还有默认那条
 *       {@code Tag.asString()}，给的是标签的打印串，{@code IntTag} 会给出
 *       {@code Optional.of("5")}。</li>
 * </ul>
 *
 * 这一族读的是钱和托管，判型松一档的意思就是【坏存档被当成好存档收进来】。
 * 所以判型照旧走「类型正好是它」，也就是 {@code get(k) instanceof 那个 Tag 类}。
 *
 * <h2>取值那一半才是真的两支不同</h2>
 *
 * 1.21.1 上 {@code NumericTag} 还是个抽象类、方法是 {@code getAsLong()} /
 * {@code getAsString()}；26.x 上 {@code LongTag}、{@code IntTag}、{@code StringTag}、
 * {@code ByteTag} 全成了 record（{@code public record LongTag(long value)}），
 * {@code NumericTag} 变成 sealed 接口，访问器是 {@code value()} 与
 * {@code longValue()} 这一套，{@code getAsLong()} 【没有了】。两支没有一个共同的取值口，
 * 这就是这层存在的理由。
 *
 * <h2>{@code *Of} 的缺省值</h2>
 *
 * 取不到时返回原版那句的缺省：{@code 0} / {@code ""} / {@code false} / 一张空表 ——
 * 漏了判型是"拿到零值"而不是抛异常。唯一的差别是原版 {@code getLong} / {@code getInt}
 * 会换算任意数值标签，这里不换算；现存调用点判过类型才取，走不到这一支。
 */
public final class Nbt {

    private Nbt() {}

    /** 这张表上所有的键。老的那两支叫 {@code getAllKeys()}。 */
    public static Set<String> keys(CompoundTag t) {
        return t.keySet();
    }

    /** 键在、而且正好是 long。 */
    public static boolean isLong(CompoundTag t, String k) {
        return t.get(k) instanceof LongTag;
    }

    /** 正好是 long 才取值，否则 0。 */
    public static long longOf(CompoundTag t, String k) {
        return t.get(k) instanceof LongTag v ? v.value() : 0L;
    }

    /** 键在、而且正好是 int。 */
    public static boolean isInt(CompoundTag t, String k) {
        return t.get(k) instanceof IntTag;
    }

    /** 正好是 int 才取值，否则 0。 */
    public static int intOf(CompoundTag t, String k) {
        return t.get(k) instanceof IntTag v ? v.value() : 0;
    }

    /** 键在、而且正好是字符串。 */
    public static boolean isString(CompoundTag t, String k) {
        return t.get(k) instanceof StringTag;
    }

    /** 正好是字符串才取值，否则空串。 */
    public static String stringOf(CompoundTag t, String k) {
        return t.get(k) instanceof StringTag v ? v.value() : "";
    }

    /**
     * 键在、而且正好是 byte。原版没有 boolean 这一种标签，
     * {@code getBoolean} 就是 {@code getByte != 0}，这里照旧。
     */
    public static boolean isBoolean(CompoundTag t, String k) {
        return t.get(k) instanceof ByteTag;
    }

    /** 正好是 byte 才按"非零即真"取值，否则 false。 */
    public static boolean booleanOf(CompoundTag t, String k) {
        return t.get(k) instanceof ByteTag v && v.value() != 0;
    }

    /** 键在、而且正好是一张表。 */
    public static boolean isCompound(CompoundTag t, String k) {
        return t.get(k) instanceof CompoundTag;
    }

    /** 正好是一张表才拿出来，否则给一张空的。 */
    public static CompoundTag compoundOf(CompoundTag t, String k) {
        Tag raw = t.get(k);
        return raw instanceof CompoundTag c ? c : new CompoundTag();
    }

    /** 键在、而且正好是一个列表。 */
    public static boolean isList(CompoundTag t, String k) {
        return t.get(k) instanceof ListTag;
    }

    /**
     * 取「元素全是表」的那个列表：不是列表、或者列表里有不是表的条目，就给一个空列表。
     *
     * <p>这一条是那两支【差得最远】的一句。老的那两支走
     * {@code getList(key, Tag.TAG_COMPOUND)}，它按列表【声明的】元素类型筛：
     * 非空而元素类型不合就返回新的空列表。26.x 那句只剩 {@code getList(key)}，
     * 什么也筛不掉，而 {@code ListTag} 上原来那个 {@code getElementType()}
     * 在这支【不再是 public】（对着 26.3 的源看过一圈公开方法，只剩
     * {@code getId()} / {@code getType()} 这些），所以这里改成逐条看。
     *
     * <p>逐条看与看声明类型在正常列表上等价；不一样的那种是"列表里混了种类"，
     * 而那恰恰是这句要拦的东西，逐条看反而拦得准。
     */
    public static ListTag compoundListOf(CompoundTag t, String k) {
        if (!(t.get(k) instanceof ListTag l)) return new ListTag();
        for (int i = 0; i < l.size(); i++) {
            if (!(l.get(i) instanceof CompoundTag)) return new ListTag();
        }
        return l;
    }

    /** 列表里第 i 个元素，按表取。老的那两支叫 {@code getCompound(int)}。 */
    public static CompoundTag compoundAt(ListTag l, int i) {
        return l.getCompoundOrEmpty(i);
    }
}
