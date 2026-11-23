package com.november.mcphone.core;

import com.november.mcphone.compat.CuriosCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * 手机在玩家身上的什么地方。
 *
 * 为什么不能继续用"哪只手"
 *
 * 界面原先只能靠右键手机打开，所以一个 InteractionHand 就够了：手机
 * 必然在某只手上，设备名也就写回那只手上的物品堆。
 *
 * 有了饰品槽和快捷键之后这个前提没了——手机可能挂在腰上、躺在背包第
 * 十七格，玩家两手空空照样能开机。设备名总得知道该写回【哪一部】手机，
 * 尤其玩家身上不止一部时。
 *
 * 为什么位置要过网络
 *
 * 改设备名这件事只能由服务端落笔（客户端说了不算），而服务端必须知道
 * 改的是哪一部。让服务端自己去找的话，玩家背包里两部手机就会改错那一部。
 *
 * 位置由客户端给出、服务端解析后【再验一次拿到的确实是手机】：客户端
 * 报一个不存在的位置或者别的物品，最坏也只是什么都没改。
 */
public interface PhoneLocation {

    /** 拿在手上 */
    final class InHand implements PhoneLocation {
        private final InteractionHand hand;

        public InHand(InteractionHand hand) {
            this.hand = hand;
        }

        public InteractionHand hand() { return hand; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof InHand)) return false;
            InHand that = (InHand) other;
            return java.util.Objects.equals(this.hand, that.hand);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(hand);
            return hash;
        }

        @Override public String toString() { return "InHand[hand=" + hand + "]"; }


        @Override
        public ItemStack resolve(Player player) {
            return player.getItemInHand(hand);
        }

        @Override
        public void writeBack(Player player, ItemStack stack) {
            // 手上那只是物品栏里的同一个对象，改完原版自会同步，无需额外动作
        }
    }

    /** 躺在背包里（含盔甲栏与副手，序号按原版 Inventory 的编号） */
    final class InInventory implements PhoneLocation {
        private final int slot;

        public InInventory(int slot) {
            this.slot = slot;
        }

        public int slot() { return slot; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof InInventory)) return false;
            InInventory that = (InInventory) other;
            return this.slot == that.slot;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(slot);
            return hash;
        }

        @Override public String toString() { return "InInventory[slot=" + slot + "]"; }


        @Override
        public ItemStack resolve(Player player) {
            Inventory inventory = player.getInventory();
            // 序号可能已经失效：玩家丢掉手机、换了容器都会让它对不上号
            if (slot < 0 || slot >= inventory.getContainerSize()) return ItemStack.EMPTY;
            return inventory.getItem(slot);
        }

        @Override
        public void writeBack(Player player, ItemStack stack) {
            // 同上，背包里的物品堆改完原版自会同步
        }
    }

    /** 挂在 Curios 的饰品槽里 */
    final class InCurio implements PhoneLocation {
        private final String slotId;
        private final int index;

        public InCurio(String slotId, int index) {
            this.slotId = slotId;
            this.index = index;
        }

        public String slotId() { return slotId; }

        public int index() { return index; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof InCurio)) return false;
            InCurio that = (InCurio) other;
            return java.util.Objects.equals(this.slotId, that.slotId)
                    && this.index == that.index;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(slotId);
            hash = 31 * hash + java.util.Objects.hashCode(index);
            return hash;
        }

        @Override public String toString() { return "InCurio[slotId=" + slotId + ", index=" + index + "]"; }


        @Override
        public ItemStack resolve(Player player) {
            return CuriosCompat.getEquipped(player, slotId, index);
        }

        @Override
        public void writeBack(Player player, ItemStack stack) {
            // 这里必须显式写回：饰品栏的同步归 Curios 管，得告诉它东西变了
            CuriosCompat.setEquipped(player, slotId, index, stack);
        }
    }

    /** 取出这个位置上的物品。位置已失效时返回空堆，调用方自行判断 */
    ItemStack resolve(Player player);

    /** 改完之后把物品写回去。只有饰品栏真的需要这一步 */
    void writeBack(Player player, ItemStack stack);

    /**
     * 从玩家身上找出一部手机，按顺手程度排序。
     *
     * 手上的优先：玩家正举着的那部显然就是他想用的。其次背包，最后饰品槽
     * ——放进饰品槽是"收起来"的意思，拿在手上的应该盖过它。
     *
     * 没装 Curios 时最后一步直接落空，不会碰到 Curios 的任何类，
     * 所以这个方法在任何情况下都能用。
     */
    static Optional<PhoneLocation> find(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (PhoneItem.isDevice(player.getItemInHand(hand))) {
                return Optional.of(new InHand(hand));
            }
        }

        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (PhoneItem.isDevice(inventory.getItem(slot))) {
                return Optional.of(new InInventory(slot));
            }
        }

        return CuriosCompat.findEquipped(player, PhoneItem::isDevice)
                .map(ref -> new InCurio(ref.slotId(), ref.index()));
    }

    //  网络

    /** 槽位 id 的长度上限，够任何合理的命名，也堵住超长字符串 */
    int MAX_SLOT_ID_LENGTH = 64;

    byte TYPE_MAIN_HAND = 0;
    byte TYPE_OFF_HAND = 1;
    byte TYPE_INVENTORY = 2;
    byte TYPE_CURIO = 3;

    /**
     * 手写而不是用固定字段的组合：这是个和类型有关的多态结构。
     *
     * 1.21.1 那边这里是一个 StreamCodec 匿名类。1.20.1 上 StreamCodec 与
     * ByteBufCodecs 都不存在（都是 1.20.5 才加的），退回 FriendlyByteBuf 上的
     * 一对静态方法，读写的字节完全一样：
     *
     *   ByteBufCodecs.VAR_INT            →  buf.writeVarInt / readVarInt
     *   ByteBufCodecs.stringUtf8(N)      →  buf.writeUtf(s, N) / readUtf(N)
     *
     * 长度上限 N 必须原样带过来。它不是装饰，是挡超长字符串的那道闸。
     */
    static void encode(PhoneLocation value, FriendlyByteBuf buf) {
        // 这里本来是 switch + record pattern，由编译器按 sealed 接口查穷尽性。
        // 那套语法 Java 21 才转正 —— 连退一步的 case InHand h 在 17 上也只是
        // 预览特性，所以整个 switch 都得换成 instanceof 链。
        //
        // 代价是穷尽性检查没了：以后给 PhoneLocation 加第四种实现，1.21.1 那支
        // 编译不过，这边会静悄悄走到最后的 else。所以那个 else 必须抛，不能省
        // —— 它是这里唯一还剩的守卫。详见 docs/PORTING.md 第 9 条
        if (value instanceof InHand) {
            buf.writeByte(((InHand) value).hand() == InteractionHand.MAIN_HAND
                    ? TYPE_MAIN_HAND : TYPE_OFF_HAND);
        } else if (value instanceof InInventory) {
            buf.writeByte(TYPE_INVENTORY);
            buf.writeVarInt(((InInventory) value).slot());
        } else if (value instanceof InCurio) {
            buf.writeByte(TYPE_CURIO);
            buf.writeUtf(((InCurio) value).slotId(), MAX_SLOT_ID_LENGTH);
            buf.writeVarInt(((InCurio) value).index());
        } else {
            throw new IllegalStateException(
                    "PhoneLocation 多了一种实现但没在这里编码: " + value.getClass().getName());
        }
    }

    static PhoneLocation decode(FriendlyByteBuf buf) {
        byte type = buf.readByte();
        switch (type) {
            case TYPE_OFF_HAND:
                return new InHand(InteractionHand.OFF_HAND);
            case TYPE_INVENTORY:
                return new InInventory(buf.readVarInt());
            case TYPE_CURIO:
                return new InCurio(
                    buf.readUtf(MAX_SLOT_ID_LENGTH),
                    buf.readVarInt());
                // 主手，以及任何不认识的类型。版本不一致或伪造客户端送来未知值时，
                // 宁可退回一个无害的默认位置，也不要抛异常打断整条连接——
                // 反正服务端还要验一次那儿是不是真有手机。Relation 那边同理
            default:
                return new InHand(InteractionHand.MAIN_HAND);
        }
    }
}
