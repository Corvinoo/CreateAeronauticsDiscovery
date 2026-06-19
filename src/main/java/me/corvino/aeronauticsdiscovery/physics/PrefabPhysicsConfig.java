package me.corvino.aeronauticsdiscovery.physics;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record PrefabPhysicsConfig(ResourceLocation template, InitialVelocity initialVelocity) {
    public static final MapCodec<PrefabPhysicsConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("template").forGetter(PrefabPhysicsConfig::template),
            InitialVelocity.CODEC.fieldOf("initial_velocity").forGetter(PrefabPhysicsConfig::initialVelocity)
    ).apply(instance, PrefabPhysicsConfig::new));
}
