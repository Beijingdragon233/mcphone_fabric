package com.november.mcphone.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Operations shared by the Gson versions bundled with Minecraft. */
public final class GsonCompat {
    private GsonCompat() {}

    public static Set<String> keys(JsonObject object) {
        Set<String> keys = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) keys.add(entry.getKey());
        return keys;
    }

    public static JsonObject copy(JsonObject object) {
        JsonObject copy = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            copy.add(entry.getKey(), copyElement(entry.getValue()));
        }
        return copy;
    }

    private static JsonElement copyElement(JsonElement value) {
        if (value.isJsonObject()) return copy(value.getAsJsonObject());
        if (value.isJsonArray()) {
            JsonArray copy = new JsonArray();
            for (JsonElement child : value.getAsJsonArray()) copy.add(copyElement(child));
            return copy;
        }
        return value;
    }
}
