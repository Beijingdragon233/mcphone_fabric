package com.november.mcphone.platform;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.Resource;

/** 26.3 用 Codec 元数据；旧版本仍由各自的 MetadataSectionSerializer 读取。 */
public final class SkinMetadata {

    private static final MetadataSectionType<Integer> TYPE = new MetadataSectionType<>(
            "mcphone_skin",
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("border", 0).forGetter(value -> value)
            ).apply(instance, value -> value))
    );

    private SkinMetadata() {}

    public static int border(Resource resource) {
        return resource.metadata().getSection(TYPE).orElse(0);
    }
}
