package me.corvino.aeronauticsdiscovery.physics.explosion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class SphereExplosion implements ExplosionStrategy {

    public static final SphereExplosion INSTANCE = new SphereExplosion();

    private SphereExplosion() {}

    @Override
    public void explode(ServerLevel level, Vec3 center, float power, boolean fire) {
        BlockPos centerPos = BlockPos.containing(center);
        int radius = Mth.ceil(power);

        float pitch = (1.0f + (level.random.nextFloat() - level.random.nextFloat()) * 0.2f) * 0.7f;
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0f, pitch);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);

        List<BlockPos> toBlow = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distSq = (double) (x * x + y * y + z * z);
                    if (distSq > power) continue;

                    BlockPos pos = centerPos.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;

                    float chance = 1.0f - (float) (Math.sqrt(distSq) / power);
                    if (level.random.nextFloat() < chance * 0.85f + 0.15f) {
                        toBlow.add(pos.immutable());
                    }
                }
            }
        }

        if (toBlow.isEmpty()) return;

        Explosion explosion = new Explosion(
                level, null,
                center.x, center.y, center.z,
                power, fire,
                Explosion.BlockInteraction.DESTROY_WITH_DECAY, toBlow
        );
        explosion.finalizeExplosion(true);
    }
}
