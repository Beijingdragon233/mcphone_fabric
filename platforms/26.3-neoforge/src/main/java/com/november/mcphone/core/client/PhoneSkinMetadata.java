package com.november.mcphone.core.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.Resource;

/** 换肤元数据解码的版本接缝。 */
final class PhoneSkinMetadata {
    private PhoneSkinMetadata() {}

    private static final MetadataSectionType<PhoneSkin.SkinMetadata> TYPE =
            new MetadataSectionType<>("mcphone_skin",
                    Codec.PASSTHROUGH.xmap(
                            value -> PhoneSkin.parseSkinMetadata(
                                    value.convert(JsonOps.INSTANCE).getValue().getAsJsonObject()),
                            value -> {
                                throw new UnsupportedOperationException("换肤元数据仅读取");
                            }));

    static PhoneSkin.SkinMetadata read(Resource resource) throws java.io.IOException {
        return resource.metadata().getSection(TYPE)
                .orElse(new PhoneSkin.SkinMetadata(0, 1));
    }
}
