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
 * {@code CompoundTag} 上「先看类型、再取值」这一对。
 *
 * <h2>为什么要这么一层</h2>
 *
 * 存档校验要的是【严格】：键在、而且类型正好是它。1.20.1 / 1.21.1 上这句话写作
 * 两句 —— {@code contains(key, Tag.TAG_LONG)} 判型，{@code getLong(key)} 取值。
 * 26.x 把取值那半全换成了 {@code Optional}（{@code getLong} 返 {@code Optional<Long>}、
 * {@code getCompound} 返 {@code Optional<CompoundTag>}、{@code getAllKeys} 改名
 * {@code keySet}……），于是两句都写不了。
 *
 * <p>看着最顺的替法是【直接拿 {@code Optional.isPresent()} 当判型】，这条是错的：
 * 26.x 的 {@code getLong} 走 {@code Tag::asLong}，{@code IntTag} 会被【换算】成 long 而返回非空；
 * {@code getString} 更松，默认那条 {@code asString} 给的是标签的打印串，
 * 一个 {@code IntTag} 会给出 {@code Optional.of("5")}。而 {@code contains(key, TAG_LONG)}
 * 在 1.21.1 上是 {@code getTagType(key) == type}（{@code 99} 那个"任意数值"是另一档），
 * 精确不相等就不算。这一族读的是钱和托管，判型松一档的意思就是【坏存档被当成好存档收进来】，
 * 所以判型必须照旧走"类型正好是它"。
 *
 * <h2>两支共同的底：{@code get(String)} 与 {@code instanceof}</h2>
 *
 * {@code CompoundTag.get(String)} 与那几个 {@code *Tag} 类名在四个目标上都在、也没改名，
 * 所以判型这一半两支其实写的是同一句话；放进这层是为了让调用点读起来是一对，
 * 不是为了绕一圈。取值那一半才是真的两支不同：1.21.1 上是 {@code getAsLong()} /
 * {@code getAsString()}（{@code NumericTag} 还是个抽象类），26.x 上 {@code LongTag}、
 * {@code StringTag} 这些已经是 record，访问器叫 {@code value()}。
 *
 * <h2>{@code *Of} 的缺省值</h2>
 *
 * 取不到（键不在或类型不对）时返回原版那句的缺省：{@code 0} / {@code ""} /
 * {@code false} / 一张空表。这样漏了判型是"拿到零值"，不是抛异常 —— 和改之前一模一样。
 * 差别只有一处：原版 {@code getLong} / {@code getInt} 会把任意数值标签换算
 * （{@code contains(key, 99)} 那一档），这里【不换算】，{@code IntTag} 上的
 * {@code longOf} 给 0。现存调用点判过类型才取，走不到这一支。
 */
public final class Nbt {

    private Nbt() {}

    /** 这张表上所有的键。26.x 改叫 {@code keySet()}。 */
    public static Set<String> keys(CompoundTag t) {
        return t.getAllKeys();
    }

    /** 键在、而且正好是 long。 */
    public static boolean isLong(CompoundTag t, String k) {
        return t.get(k) instanceof LongTag;
    }

    /** 正好是 long 才取值，否则 0。 */
    public static long longOf(CompoundTag t, String k) {
        return t.get(k) instanceof LongTag v ? v.getAsLong() : 0L;
    }

    /** 键在、而且正好是 int。 */
    public static boolean isInt(CompoundTag t, String k) {
        return t.get(k) instanceof IntTag;
    }

    /** 正好是 int 才取值，否则 0。 */
    public static int intOf(CompoundTag t, String k) {
        return t.get(k) instanceof IntTag v ? v.getAsInt() : 0;
    }

    /** 键在、而且正好是字符串。 */
    public static boolean isString(CompoundTag t, String k) {
        return t.get(k) instanceof StringTag;
    }

    /** 正好是字符串才取值，否则空串（这一条与原版 {@code getString} 完全一致）。 */
    public static String stringOf(CompoundTag t, String k) {
        return t.get(k) instanceof StringTag v ? v.getAsString() : "";
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
        return t.get(k) instanceof ByteTag v && v.getAsByte() != 0;
    }

    /** 键在、而且正好是一张表。 */
    public static boolean isCompound(CompoundTag t, String k) {
        return t.get(k) instanceof CompoundTag;
    }

    /** 正好是一张表才拿出来，否则给一张空的（与原版 {@code getCompound} 一致）。 */
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
     * <p>这一条治的是原版的 {@code getList(key, Tag.TAG_COMPOUND)} —— 它按列表【声明的】
     * 元素类型筛：非空而元素类型不合就返回新的空列表。26.x 那句只剩 {@code getList(key)}，
     * 筛不掉任何东西，所以那一支是逐条看的，见它自己的注释。
     */
    public static ListTag compoundListOf(CompoundTag t, String k) {
        return t.getList(k, Tag.TAG_COMPOUND);
    }

    /** 列表里第 i 个元素，按表取。26.x 改叫 {@code getCompoundOrEmpty(int)}。 */
    public static CompoundTag compoundAt(ListTag l, int i) {
        return l.getCompound(i);
    }
}
