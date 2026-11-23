package com.november.mcphone.core.script.server;

import com.november.mcphone.MCphone;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * worker 产出的"要对世界做什么"（施工方案 §15.5）。
 *
 * <p><b>意图不是效果</b>：worker 只能产出它，落地一律回主线程，而且落地前要重查授权与能力。
 *
 * <h2>本步定下来的种类（S18）</h2>
 *
 * <ul>
 *   <li>{@code item.give}（capability {@code item.give}）—— 给发起者物品；</li>
 *   <li>{@code loot.roll}（capability {@code loot.roll}）—— 掷一张战利品表并给发起者；</li>
 *   <li>{@code attr.grant} / {@code attr.revoke}（capability {@code attr.grant}）——
 *       给自己加/撤一条<b>瞬时</b>属性修饰符；</li>
 *   <li>{@code effect.give}（capability {@code effect.give}）—— 给自己一个药水效果
 *       （<b>有时限</b>，到期自然消失，不写存档）。</li>
 * </ul>
 *
 * <p>payload 用 {@code DataOutputStream} 写：只有字符串与整数/浮点，不碰注册表；
 * 每条的字段上限在 {@link #itemGive} 等工厂里校验，坏数据在产出点就拒。
 *
 * @param kind    意图种类
 * @param payload 这一条意图的内容
 */
public final class ActionIntent {
    private final String kind;
    private final byte[] payload;

    public String kind() { return kind; }

    public byte[] payload() { return payload; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ActionIntent)) return false;
        ActionIntent that = (ActionIntent) other;
        return java.util.Objects.equals(this.kind, that.kind)
                && java.util.Objects.equals(this.payload, that.payload);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(payload);
        return hash;
    }


    public static final String ITEM_GIVE = "item.give";
    public static final String LOOT_ROLL = "loot.roll";
    public static final String ATTR_GRANT = "attr.grant";
    public static final String ATTR_REVOKE = "attr.revoke";
    public static final String EFFECT_GIVE = "effect.give";

    /** 一次意图最多带多少个物品（与 {@code ItemRef.MAX_BATCH} 同一量级）。 */
    public static final int MAX_GIVE = 27;

    /** 属性修饰符 id 的自定义部分上限。 */
    public static final int MAX_ID = 64;

    /** 药水效果最长 1 小时（72000 tick）：S18 还没接配额（§28），先给一条写死的上限。 */
    public static final int MAX_EFFECT_TICKS = 72_000;

    /** 效果等级（amplifier）上限：原版网络格式是 8 位。 */
    public static final int MAX_AMPLIFIER = 255;

    public ActionIntent(String kind, byte[] payload) {
        if (kind == null || kind.isEmpty()) throw new IllegalArgumentException("ActionIntent.kind 不能为空");
        if (payload == null) payload = new byte[0];
        if (payload.length > 4096) throw new IllegalArgumentException("ActionIntent.payload 超过 4096 字节");
        this.kind = kind;
        this.payload = payload;
    }

    /** 这条意图需要哪个能力。没登记的种类返回 null（落地时会拒）。 */
    public String capability() {
        switch (kind) {
            case ITEM_GIVE:
                return "item.give";
            case LOOT_ROLL:
                return "loot.roll";
            case ATTR_GRANT:
            case ATTR_REVOKE:
                return "attr.grant";
            case EFFECT_GIVE:
                return "effect.give";
            default:
                return null;
        }
    }

    // ---------------------------------------------------------------- 工厂

    public static ActionIntent itemGive(String itemId, int count, String customName) {
        checkId(itemId, "item.give 的物品 id");
        if (count < 1 || count > MAX_GIVE) {
            throw new IllegalArgumentException("item.give 的数量要在 1.." + MAX_GIVE + "：" + count);
        }
        return new ActionIntent(ITEM_GIVE, write(w -> {
            w.writeUTF(itemId);
            w.writeInt(count);
            w.writeUTF(customName == null ? "" : clip(customName, 64));
        }));
    }

    public static ActionIntent lootRoll(String tableId) {
        checkId(tableId, "loot.roll 的表 id");
        return new ActionIntent(LOOT_ROLL, write(w -> w.writeUTF(tableId)));
    }

    public static ActionIntent attrGrant(String attributeId, double amount, int operation, String modifierKey) {
        checkId(attributeId, "attr.grant 的属性 id");
        if (!Double.isFinite(amount) || Math.abs(amount) > 1_000_000d) {
            throw new IllegalArgumentException("attr.grant 的数值不合法：" + amount);
        }
        if (operation < 0 || operation > 2) throw new IllegalArgumentException("attr.grant 的 operation 不合法：" + operation);
        checkKey(modifierKey);
        return new ActionIntent(ATTR_GRANT, write(w -> {
            w.writeUTF(attributeId);
            w.writeDouble(amount);
            w.writeInt(operation);
            w.writeUTF(modifierKey);
        }));
    }

    public static ActionIntent attrRevoke(String attributeId, String modifierKey) {
        checkId(attributeId, "attr.revoke 的属性 id");
        checkKey(modifierKey);
        return new ActionIntent(ATTR_REVOKE, write(w -> {
            w.writeUTF(attributeId);
            w.writeUTF(modifierKey);
        }));
    }

    public static ActionIntent effectGive(String effectId, int durationTicks, int amplifier) {
        checkId(effectId, "effect.give 的效果 id");
        if (durationTicks < 1 || durationTicks > MAX_EFFECT_TICKS) {
            throw new IllegalArgumentException("effect.give 的时长要在 1.." + MAX_EFFECT_TICKS + " tick：" + durationTicks);
        }
        if (amplifier < 0 || amplifier > MAX_AMPLIFIER) {
            throw new IllegalArgumentException("effect.give 的等级要在 0.." + MAX_AMPLIFIER + "：" + amplifier);
        }
        return new ActionIntent(EFFECT_GIVE, write(w -> {
            w.writeUTF(effectId);
            w.writeInt(durationTicks);
            w.writeInt(amplifier);
        }));
    }

    // ---------------------------------------------------------------- 读

    public static final class Give {
        private final String itemId;
        private final int count;
        private final String customName;

        public Give(String itemId, int count, String customName) {
            this.itemId = itemId;
            this.count = count;
            this.customName = customName;
        }

        public String itemId() { return itemId; }

        public int count() { return count; }

        public String customName() { return customName; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Give)) return false;
            Give that = (Give) other;
            return java.util.Objects.equals(this.itemId, that.itemId)
                    && this.count == that.count
                    && java.util.Objects.equals(this.customName, that.customName);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(itemId);
            hash = 31 * hash + java.util.Objects.hashCode(count);
            hash = 31 * hash + java.util.Objects.hashCode(customName);
            return hash;
        }

        @Override public String toString() { return "Give[itemId=" + itemId + ", count=" + count + ", customName=" + customName + "]"; }

    }

    public static final class Roll {
        private final String tableId;

        public Roll(String tableId) {
            this.tableId = tableId;
        }

        public String tableId() { return tableId; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Roll)) return false;
            Roll that = (Roll) other;
            return java.util.Objects.equals(this.tableId, that.tableId);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(tableId);
            return hash;
        }

        @Override public String toString() { return "Roll[tableId=" + tableId + "]"; }

    }

    public static final class Attr {
        private final String attributeId;
        private final double amount;
        private final int operation;
        private final String modifierKey;

        public Attr(String attributeId, double amount, int operation, String modifierKey) {
            this.attributeId = attributeId;
            this.amount = amount;
            this.operation = operation;
            this.modifierKey = modifierKey;
        }

        public String attributeId() { return attributeId; }

        public double amount() { return amount; }

        public int operation() { return operation; }

        public String modifierKey() { return modifierKey; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Attr)) return false;
            Attr that = (Attr) other;
            return java.util.Objects.equals(this.attributeId, that.attributeId)
                    && Double.compare(this.amount, that.amount) == 0
                    && this.operation == that.operation
                    && java.util.Objects.equals(this.modifierKey, that.modifierKey);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(attributeId);
            hash = 31 * hash + java.util.Objects.hashCode(amount);
            hash = 31 * hash + java.util.Objects.hashCode(operation);
            hash = 31 * hash + java.util.Objects.hashCode(modifierKey);
            return hash;
        }

        @Override public String toString() { return "Attr[attributeId=" + attributeId + ", amount=" + amount + ", operation=" + operation + ", modifierKey=" + modifierKey + "]"; }

    }

    public static final class Effect {
        private final String effectId;
        private final int durationTicks;
        private final int amplifier;

        public Effect(String effectId, int durationTicks, int amplifier) {
            this.effectId = effectId;
            this.durationTicks = durationTicks;
            this.amplifier = amplifier;
        }

        public String effectId() { return effectId; }

        public int durationTicks() { return durationTicks; }

        public int amplifier() { return amplifier; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Effect)) return false;
            Effect that = (Effect) other;
            return java.util.Objects.equals(this.effectId, that.effectId)
                    && this.durationTicks == that.durationTicks
                    && this.amplifier == that.amplifier;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(effectId);
            hash = 31 * hash + java.util.Objects.hashCode(durationTicks);
            hash = 31 * hash + java.util.Objects.hashCode(amplifier);
            return hash;
        }

        @Override public String toString() { return "Effect[effectId=" + effectId + ", durationTicks=" + durationTicks + ", amplifier=" + amplifier + "]"; }

    }

    public Give asGive() {
        return read(ITEM_GIVE, r -> new Give(r.readUTF(), r.readInt(), r.readUTF()));
    }

    public Roll asRoll() {
        return read(LOOT_ROLL, r -> new Roll(r.readUTF()));
    }

    public Attr asAttr() {
        return read(ATTR_GRANT, r -> new Attr(r.readUTF(), r.readDouble(), r.readInt(), r.readUTF()));
    }

    public Attr asRevoke() {
        return read(ATTR_REVOKE, r -> new Attr(r.readUTF(), 0d, 0, r.readUTF()));
    }

    public Effect asEffect() {
        return read(EFFECT_GIVE, r -> new Effect(r.readUTF(), r.readInt(), r.readInt()));
    }

    // ---------------------------------------------------------------- 内部

    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private interface Reader<T> {
        T read(DataInputStream in) throws IOException;
    }

    private static byte[] write(Writer body) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            body.write(out);
        } catch (IOException e) {
            throw new IllegalStateException("ActionIntent 编码失败", e);
        }
        return bytes.toByteArray();
    }

    private <T> T read(String expectedKind, Reader<T> body) {
        if (!expectedKind.equals(kind)) {
            throw new IllegalStateException("意图种类对不上：要 " + expectedKind + "，实际 " + kind);
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            return body.read(in);
        } catch (IOException e) {
            throw new IllegalStateException("ActionIntent 解不开（" + kind + "）", e);
        }
    }

    /** id 必须是合法的 {@code namespace:path} 且 ≤ 64 字符。 */
    private static void checkId(String id, String what) {
        checkKey(id);
        if (ResourceLocation.tryParse(id) == null) {
            throw new IllegalArgumentException(what + " 不是合法的 ResourceLocation：" + id);
        }
    }

    private static void checkKey(String key) {
        if (key == null || key.isEmpty() || key.length() > MAX_ID) {
            throw new IllegalArgumentException("id 长度要在 1.." + MAX_ID + "：" + key);
        }
        for (int i = 0; i < key.length(); i++) {
            if (Character.isISOControl(key.charAt(i))) {
                throw new IllegalArgumentException("id 里不能有控制字符：" + key);
            }
        }
    }

    private static String clip(String s, int max) {
        if (s.length() <= max) return s;
        MCphone.LOGGER.warn("[MCphone] ActionIntent 的文本被截到 {} 字（原文 {} 字）", max, s.length());
        return s.substring(0, max);
    }

    /** 给日志用：不打印 payload（可能带玩家输入的名字）。 */
    @Override
    public String toString() {
        return "ActionIntent[" + kind.toLowerCase(Locale.ROOT) + " " + payload.length + "B]";
    }
}
