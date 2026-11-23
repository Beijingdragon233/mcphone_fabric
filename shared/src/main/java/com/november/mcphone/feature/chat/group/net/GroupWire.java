package com.november.mcphone.feature.chat.group.net;

import java.util.UUID;
import com.november.mcphone.core.net.Wire;
import com.november.mcphone.feature.chat.ChatMessage;
import com.november.mcphone.feature.chat.ChatImage;
import com.november.mcphone.feature.chat.ChatDeletionResult;
import com.november.mcphone.feature.chat.TextBody;
import com.november.mcphone.feature.chat.group.*;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** 群协议的公共有界部分；消息正文 codec 由版本层注入，不把 StreamCodec 带进 shared。 */
public final class GroupWire {
    private GroupWire() {}
    public static void writeCommand(GroupCommand value, FriendlyByteBuf b) {
        b.writeUUID(value.request());
        int kind = value instanceof GroupCommand.ListRooms ? 0 : value instanceof GroupCommand.Create ? 1
                : value instanceof GroupCommand.History ? 2 : value instanceof GroupCommand.SendText ? 3
                : value instanceof GroupCommand.Delete ? 4 : value instanceof GroupCommand.MarkRead ? 5
                : value instanceof GroupCommand.Details ? 6 : value instanceof GroupCommand.Leave ? 7
                : value instanceof GroupCommand.Dissolve ? 8 : value instanceof GroupCommand.Rename ? 9
                : value instanceof GroupCommand.Images ? 10 : value instanceof GroupCommand.Upload ? 11
                : value instanceof GroupCommand.Invite ? 12 : value instanceof GroupCommand.Notices ? 13
                : value instanceof GroupCommand.RespondInvitation ? 14 : -1;
        if (kind < 0) throw new IllegalArgumentException("群请求尚未登记编码");
        b.writeVarInt(kind);
        if (value.group() != null) b.writeUUID(value.group());
        if (value instanceof GroupCommand.Create) {
            b.writeUtf(((GroupCommand.Create) value).name(), GroupLimits.NAME_LENGTH); Wire.writeList(b, ((GroupCommand.Create) value).members(), GroupLimits.MEMBERS - 1, (id, buf) -> buf.writeUUID(id));
        } else if (value instanceof GroupCommand.SendText) { b.writeUtf(((GroupCommand.SendText) value).text(), TextBody.MAX_LENGTH); com.november.mcphone.feature.chat.net.ChatReplyWire.writeId(b,((GroupCommand.SendText) value).reply()); }
        else if (value instanceof GroupCommand.Delete) b.writeUUID(((GroupCommand.Delete) value).message());
        else if (value instanceof GroupCommand.MarkRead) b.writeVarLong(((GroupCommand.MarkRead) value).sequence());
        else if (value instanceof GroupCommand.Rename) b.writeUtf(((GroupCommand.Rename) value).name(), GroupLimits.NAME_LENGTH);
        else if (value instanceof GroupCommand.Invite) Wire.writeList(b, ((GroupCommand.Invite) value).members(), GroupLimits.MEMBERS - 1, (id, buf) -> buf.writeUUID(id));
        else if (value instanceof GroupCommand.RespondInvitation) { b.writeUUID(((GroupCommand.RespondInvitation) value).invitation()); b.writeBoolean(((GroupCommand.RespondInvitation) value).accept()); }
        else if (value instanceof GroupCommand.Images) Wire.writeList(b, ((GroupCommand.Images) value).images(), GroupLimits.IMAGE_REQUEST_IDS, (id, buf) -> buf.writeUUID(id));
        else if (value instanceof GroupCommand.Upload) {
            b.writeVarInt(((GroupCommand.Upload) value).width()); b.writeVarInt(((GroupCommand.Upload) value).height()); b.writeVarInt(((GroupCommand.Upload) value).frames()); b.writeVarInt(((GroupCommand.Upload) value).frameMs());
            b.writeVarInt(((GroupCommand.Upload) value).index()); b.writeVarInt(((GroupCommand.Upload) value).count()); Wire.writeBytes(b, ((GroupCommand.Upload) value).data(), ChatImage.CHUNK_BYTES);
        }
    }
    public static GroupCommand readCommand(FriendlyByteBuf b) {
        UUID request = b.readUUID(); int kind = b.readVarInt();
        if (kind == 0) return new GroupCommand.ListRooms(request);
        if (kind == 1) return new GroupCommand.Create(request, b.readUtf(GroupLimits.NAME_LENGTH), Wire.readList(b, GroupLimits.MEMBERS - 1, buf -> buf.readUUID()));
        if (kind == 13) return new GroupCommand.Notices(request);
        if (kind < 2 || kind > 14) throw new DecoderException("未知群请求 " + kind);
        UUID group = b.readUUID();
        switch (kind) {
            case 2:
                return new GroupCommand.History(request, group);
            case 3:
                return new GroupCommand.SendText(request, group, b.readUtf(TextBody.MAX_LENGTH), com.november.mcphone.feature.chat.net.ChatReplyWire.readId(b));
            case 4:
                return new GroupCommand.Delete(request, group, b.readUUID());
            case 5:
                return new GroupCommand.MarkRead(request, group, b.readVarLong());
            case 6:
                return new GroupCommand.Details(request, group);
            case 7:
                return new GroupCommand.Leave(request, group);
            case 8:
                return new GroupCommand.Dissolve(request, group);
            case 9:
                return new GroupCommand.Rename(request, group, b.readUtf(GroupLimits.NAME_LENGTH));
            case 10:
                return new GroupCommand.Images(request, group, Wire.readList(b, GroupLimits.IMAGE_REQUEST_IDS, buf -> buf.readUUID()));
            case 11:
                return new GroupCommand.Upload(request, group, b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), Wire.readBytes(b, ChatImage.CHUNK_BYTES));
            case 12:
                return new GroupCommand.Invite(request, group, Wire.readList(b, GroupLimits.MEMBERS - 1, buf -> buf.readUUID()));
            default:
                return new GroupCommand.RespondInvitation(request, group, b.readUUID(), b.readBoolean());
        }
    }
    public static void writeReply(GroupReply value, FriendlyByteBuf b, BiConsumer<ChatMessage, FriendlyByteBuf> message) {
        int kind = value instanceof GroupReply.Directory ? 0 : value instanceof GroupReply.History ? 1
                : value instanceof GroupReply.Message ? 2 : value instanceof GroupReply.Result ? 3
                : value instanceof GroupReply.Details ? 4 : value instanceof GroupReply.Deleted ? 5
                : value instanceof GroupReply.Updated ? 6 : value instanceof GroupReply.Notices ? 7 : -1;
        if (kind < 0) throw new IllegalArgumentException("群响应尚未登记编码");
        b.writeVarInt(kind);
        if (value instanceof GroupReply.Directory) Wire.writeList(b, ((GroupReply.Directory) value).groups(), GroupLimits.GROUPS_PER_PLAYER, (s, buf) -> summary(s, buf, message));
        else if (value instanceof GroupReply.History) {
            b.writeUUID(((GroupReply.History) value).request()); b.writeUUID(((GroupReply.History) value).group()); b.writeVarLong(((GroupReply.History) value).revision()); b.writeVarLong(((GroupReply.History) value).sequence()); info(((GroupReply.History) value).info(), b);
            Wire.writeList(b, ((GroupReply.History) value).messages(), GroupLimits.MESSAGES, (m, buf) -> entry(m, buf, message));
        } else if (value instanceof GroupReply.Message) {
            b.writeUUID(((GroupReply.Message) value).request()); b.writeVarLong(((GroupReply.Message) value).revision()); summary(((GroupReply.Message) value).summary(), b, message);
        } else if (value instanceof GroupReply.Result) {
            b.writeUUID(((GroupReply.Result) value).request()); b.writeBoolean(((GroupReply.Result) value).group() != null); if (((GroupReply.Result) value).group() != null) b.writeUUID(((GroupReply.Result) value).group()); b.writeVarInt(((GroupReply.Result) value).status().wireId());
        } else if (value instanceof GroupReply.Details) { b.writeUUID(((GroupReply.Details) value).request()); b.writeVarLong(((GroupReply.Details) value).revision()); info(((GroupReply.Details) value).info(), b); }
        else if (value instanceof GroupReply.Updated) { b.writeVarLong(((GroupReply.Updated) value).revision()); info(((GroupReply.Updated) value).info(), b); }
        else if (value instanceof GroupReply.Notices) Wire.writeList(b, ((GroupReply.Notices) value).invitations(), GroupLimits.INVITATIONS_PER_PLAYER, GroupWire::notice);
        else if (value instanceof GroupReply.Deleted) {
            b.writeUUID(((GroupReply.Deleted) value).request()); b.writeUUID(((GroupReply.Deleted) value).group()); b.writeUUID(((GroupReply.Deleted) value).message()); b.writeVarInt(((GroupReply.Deleted) value).status().wireId()); b.writeVarLong(((GroupReply.Deleted) value).revision());
            Wire.writeList(b, ((GroupReply.Deleted) value).messages(), GroupLimits.MESSAGES, (m, buf) -> entry(m, buf, message));
        }
    }
    public static GroupReply readReply(FriendlyByteBuf b, Function<FriendlyByteBuf, ChatMessage> message) {
        switch (b.readVarInt()) {
            case 0:
                return new GroupReply.Directory(Wire.readList(b, GroupLimits.GROUPS_PER_PLAYER, buf -> summary(buf, message)));
            case 1:
                return new GroupReply.History(b.readUUID(), b.readUUID(), nonnegative(b.readVarLong()), nonnegative(b.readVarLong()), info(b), Wire.readList(b, GroupLimits.MESSAGES, buf -> entry(buf, message)));
            case 2:
                return new GroupReply.Message(b.readUUID(), nonnegative(b.readVarLong()), summary(b, message));
            case 3:
                return new GroupReply.Result(b.readUUID(), b.readBoolean() ? b.readUUID() : null, GroupStatus.fromWire(b.readVarInt()));
            case 4:
                return new GroupReply.Details(b.readUUID(), nonnegative(b.readVarLong()), info(b));
            case 6:
                return new GroupReply.Updated(nonnegative(b.readVarLong()), info(b));
            case 7:
                return new GroupReply.Notices(Wire.readList(b, GroupLimits.INVITATIONS_PER_PLAYER, GroupWire::notice));
            case 5:
                return new GroupReply.Deleted(b.readUUID(), b.readUUID(), b.readUUID(), ChatDeletionResult.fromWire(b.readVarInt()), nonnegative(b.readVarLong()), Wire.readList(b, GroupLimits.MESSAGES, buf -> entry(buf, message)));
            default:
                throw new DecoderException("未知群响应");
        }
    }
    private static void notice(GroupInviteNotice v, FriendlyByteBuf b) {
        b.writeUUID(v.id()); b.writeUUID(v.group()); b.writeUtf(v.name(), GroupLimits.NAME_LENGTH);
        b.writeUUID(v.inviter().id()); b.writeUtf(v.inviter().name(), GroupLimits.NAME_LENGTH);
        b.writeVarLong(v.createdAt()); b.writeVarLong(v.expiresAt());
    }
    private static GroupInviteNotice notice(FriendlyByteBuf b) {
        return new GroupInviteNotice(b.readUUID(), b.readUUID(), b.readUtf(GroupLimits.NAME_LENGTH),
                new GroupMember(b.readUUID(), b.readUtf(GroupLimits.NAME_LENGTH)), nonnegative(b.readVarLong()), nonnegative(b.readVarLong()));
    }
    private static void entry(GroupMessage m, FriendlyByteBuf b, BiConsumer<ChatMessage, FriendlyByteBuf> codec) {
        b.writeVarLong(m.sequence()); b.writeUtf(m.senderName(), GroupLimits.NAME_LENGTH); codec.accept(m.message(), b);
    }
    private static GroupMessage entry(FriendlyByteBuf b, Function<FriendlyByteBuf, ChatMessage> codec) {
        return new GroupMessage(b.readVarLong(), b.readUtf(GroupLimits.NAME_LENGTH), codec.apply(b));
    }
    private static void info(GroupInfo v, FriendlyByteBuf b) {
        b.writeUUID(v.id()); b.writeUtf(v.name(), GroupLimits.NAME_LENGTH); b.writeUUID(v.owner());
        Wire.writeList(b, v.members(), GroupLimits.MEMBERS, (m, buf) -> { buf.writeUUID(m.id()); buf.writeUtf(m.name(), GroupLimits.NAME_LENGTH); });
    }
    private static GroupInfo info(FriendlyByteBuf b) {
        return new GroupInfo(b.readUUID(), b.readUtf(GroupLimits.NAME_LENGTH), b.readUUID(),
                Wire.readList(b, GroupLimits.MEMBERS, buf -> new GroupMember(buf.readUUID(), buf.readUtf(GroupLimits.NAME_LENGTH))));
    }
    private static void summary(GroupSummary v, FriendlyByteBuf b, BiConsumer<ChatMessage, FriendlyByteBuf> codec) {
        b.writeUUID(v.id()); b.writeUtf(v.name(), GroupLimits.NAME_LENGTH); b.writeUUID(v.owner()); b.writeVarInt(v.memberCount());
        Wire.writeList(b, v.portraits(), 4, (id, buf) -> buf.writeUUID(id)); b.writeBoolean(v.last().isPresent());
        v.last().ifPresent(m -> entry(m, b, codec)); b.writeVarInt(v.unread());
    }
    private static GroupSummary summary(FriendlyByteBuf b, Function<FriendlyByteBuf, ChatMessage> codec) {
        return new GroupSummary(b.readUUID(), b.readUtf(GroupLimits.NAME_LENGTH), b.readUUID(), b.readVarInt(),
                Wire.readList(b, 4, buf -> buf.readUUID()), b.readBoolean() ? Optional.of(entry(b, codec)) : Optional.empty(), b.readVarInt());
    }
    private static long nonnegative(long value) { if (value < 0) throw new DecoderException("负的群版本/序号"); return value; }
}
