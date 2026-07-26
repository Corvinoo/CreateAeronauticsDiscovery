package me.corvino.aeronauticsdiscovery.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record VillagePoolInjectionConfig(
        ResourceLocation targetPool,
        ResourceLocation template,
        ResourceLocation processor,
        int weight,
        String projection
) {
    public static final Codec<VillagePoolInjectionConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("target_pool").forGetter(VillagePoolInjectionConfig::targetPool),
            ResourceLocation.CODEC.fieldOf("template").forGetter(VillagePoolInjectionConfig::template),
            ResourceLocation.CODEC.optionalFieldOf("processor", ResourceLocation.parse("minecraft:empty")).forGetter(VillagePoolInjectionConfig::processor),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(VillagePoolInjectionConfig::weight),
            Codec.STRING.optionalFieldOf("projection", "rigid").forGetter(VillagePoolInjectionConfig::projection)
    ).apply(instance, VillagePoolInjectionConfig::new));
}
