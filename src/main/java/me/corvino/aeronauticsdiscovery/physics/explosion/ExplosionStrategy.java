package me.corvino.aeronauticsdiscovery.physics.explosion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface ExplosionStrategy {
    void explode(ServerLevel level, Vec3 center, float power, boolean fire);
}
