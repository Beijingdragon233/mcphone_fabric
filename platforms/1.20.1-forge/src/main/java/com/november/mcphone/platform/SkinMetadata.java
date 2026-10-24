package com.november.mcphone.platform;

import com.google.gson.JsonObject;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;

public final class SkinMetadata {

    private static final MetadataSectionSerializer<Integer> TYPE = new MetadataSectionSerializer<>() {
        @Override public String getMetadataSectionName() { return "mcphone_skin"; }
        @Override public Integer fromJson(JsonObject json) {
            return json.has("border") ? json.get("border").getAsInt() : 0;
        }
    };

    private SkinMetadata() {}

    public static int border(Resource resource) {
        try {
            return resource.metadata().getSection(TYPE).orElse(0);
        } catch (java.io.IOException ignored) {
            return 0;
        }
    }
}
