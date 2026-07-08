package me.corvino.aeronauticsdiscovery.physics.explosion;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class VanillaExplosion implements ExplosionStrategy {

    public static final VanillaExplosion INSTANCE = new VanillaExplosion();

    private VanillaExplosion() {}

    @Override
    public void explode(ServerLevel level, Vec3 center, float power, boolean fire) {
        level.explode(
                null, null, null,
                center.x, center.y, center.z, power, fire,
                Level.ExplosionInteraction.TNT,
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
                SoundEvents.GENERIC_EXPLODE
        );
    }
}
