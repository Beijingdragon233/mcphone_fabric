package com.november.mcphone.feature.chat.client.favorite;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 收藏文件的固定字段契约。缺字段或类型错误不能被 Gson 默认值伪装成合法私聊记录。 */
final class FavoriteCodec {
    private static final int FORMAT = 1;
    private static final Gson JSON = new Gson();
    private FavoriteCodec() {}

    static String encode(Map<String, FavoriteText> values) {
        var doc = new JsonObject();
        doc.addProperty("format", FORMAT);
        doc.add("entries", JSON.toJsonTree(values.values()));
        return JSON.toJson(doc);
    }

    static Map<String, FavoriteText> decode(String json) {
        var doc = JsonParser.parseString(json).getAsJsonObject();
        if (integer(doc, "format") != FORMAT) throw new IllegalArgumentException("未知收藏格式");
        var entries = field(doc, "entries").getAsJsonArray();
        if (entries.size() > FavoriteStore.MAX_ENTRIES) throw new IllegalArgumentException("收藏数量超限");
        var values = new LinkedHashMap<String, FavoriteText>();
        for (var raw : entries) {
            var row = raw.getAsJsonObject();
            var group = field(row, "group");
            if (!group.isJsonPrimitive() || !group.getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException("收藏会话类型错误");
            var item = new FavoriteText(uuid(row, "message"), uuid(row, "target"), group.getAsBoolean(),
                    uuid(row, "sender"), string(row, "senderName"), string(row, "sourceName"), string(row, "text"),
                    integer(row, "messageTime"), integer(row, "savedTime"));
            if (values.putIfAbsent(item.key(), item) != null) throw new IllegalArgumentException("收藏键重复");
        }
        return values;
    }

    private static JsonElement field(JsonObject object, String key) {
        var value = object.get(key);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("收藏缺少字段 " + key);
        return value;
    }

    private static String string(JsonObject object, String key) {
        var value = field(object, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("收藏字符串类型错误 " + key);
        return value.getAsString();
    }

    private static UUID uuid(JsonObject object, String key) {
        String text = string(object, key);
        var id = UUID.fromString(text);
        if (!id.toString().equalsIgnoreCase(text)) throw new IllegalArgumentException("收藏 UUID 格式错误");
        return id;
    }

    private static long integer(JsonObject object, String key) {
        var value = field(object, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("收藏整数类型错误 " + key);
        return value.getAsBigDecimal().longValueExact();
    }
}
