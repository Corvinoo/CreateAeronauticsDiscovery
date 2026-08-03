package me.corvino.aeronauticsdiscovery.physics;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotPlan;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Optional;

public record PrefabPhysicsConfig(
        ResourceLocation template,
        InitialVelocity initialVelocity,
        @Nullable AutopilotPlan plan
) {
    public static final MapCodec<PrefabPhysicsConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("template").forGetter(PrefabPhysicsConfig::template),
            InitialVelocity.CODEC.fieldOf("initial_velocity").forGetter(PrefabPhysicsConfig::initialVelocity),
            AutopilotPlan.CODEC.codec().optionalFieldOf("plan").forGetter(config -> Optional.ofNullable(config.plan()))
    ).apply(instance, (template, velocity, plan) -> new PrefabPhysicsConfig(template, velocity, plan.orElse(null))));
}
