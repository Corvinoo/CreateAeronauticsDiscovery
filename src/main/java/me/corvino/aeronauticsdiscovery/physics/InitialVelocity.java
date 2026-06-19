package me.corvino.aeronauticsdiscovery.physics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

public record InitialVelocity(Vec3 linear, Vec3 angular, boolean impulse) {
    public static final InitialVelocity NONE = new InitialVelocity(Vec3.ZERO, Vec3.ZERO, false);

    public static final MapCodec<InitialVelocity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Vec3.CODEC.optionalFieldOf("linear", Vec3.ZERO).forGetter(InitialVelocity::linear),
            Vec3.CODEC.optionalFieldOf("angular", Vec3.ZERO).forGetter(InitialVelocity::angular),
            Codec.BOOL.optionalFieldOf("impulse", false).forGetter(InitialVelocity::impulse)
    ).apply(instance, InitialVelocity::new));
}
