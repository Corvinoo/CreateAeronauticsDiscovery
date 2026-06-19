package me.corvino.aeronauticsdiscovery.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import net.minecraft.resources.ResourceLocation;

public record FlyoverEventConfig(
        ResourceLocation template,
        int minAltitude,
        int maxAltitude,
        int horizontalOffset,
        int weight,
        InitialVelocity velocity,
        boolean randomizeYaw
) {
    public static final Codec<FlyoverEventConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("template").forGetter(FlyoverEventConfig::template),
            Codec.INT.optionalFieldOf("min_altitude", 200).forGetter(FlyoverEventConfig::minAltitude),
            Codec.INT.optionalFieldOf("max_altitude", 280).forGetter(FlyoverEventConfig::maxAltitude),
            Codec.INT.optionalFieldOf("horizontal_offset", 80).forGetter(FlyoverEventConfig::horizontalOffset),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(FlyoverEventConfig::weight),
            InitialVelocity.CODEC.codec().optionalFieldOf("initial_velocity", InitialVelocity.NONE).forGetter(FlyoverEventConfig::velocity),
            Codec.BOOL.optionalFieldOf("randomize_yaw", true).forGetter(FlyoverEventConfig::randomizeYaw)
    ).apply(instance, FlyoverEventConfig::new));
}
