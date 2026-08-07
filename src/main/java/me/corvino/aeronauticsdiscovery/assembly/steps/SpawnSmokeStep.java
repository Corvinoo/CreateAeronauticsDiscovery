package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;


public class SpawnSmokeStep extends AssemblyStep {
    private static final int SMOKE_TICKS = 40;
    private static final int EXPLOSION_PER_TICK = 14;

    private boolean spawned = false;
    private int tick = 0;

    @Override
    protected int timeoutTicks() {
        return 100;
    }

    @Override
    protected void build(Sequence seq) {
        seq
                .require(ctx -> ctx.assemblyResult != null,
                        "assemblyResult is missing; cannot spawn spawn smoke")
                .completeIf(ctx -> spawned)
                .run(this::spawnCloud)
                .delay(1);
    }

    private void spawnCloud(AssemblyContext ctx) {
        assert ctx.assemblyResult != null;
        AABB bounds = ctx.assemblyResult.subLevel().getPlot().getBoundingBox().toAABB();
        double cx = bounds.getCenter().x;
        double cy = bounds.getCenter().y;
        double cz = bounds.getCenter().z;
        double radiusX = bounds.getXsize() * 0.5;
        double radiusY = bounds.getYsize() * 0.5;
        double radiusZ = bounds.getZsize() * 0.5;

        broadcast(ctx.level, ParticleTypes.EXPLOSION, cx, cy, cz,
                EXPLOSION_PER_TICK, radiusX, radiusY, radiusZ);

        if (++tick >= SMOKE_TICKS) {
            spawned = true;
        }
    }

    private static void broadcast(ServerLevel level, ParticleOptions type,
                                  double x, double y, double z, int count,
                                  double xd, double yd, double zd) {
        for (ServerPlayer player : level.players()) {
            level.sendParticles(player, type, true, x, y, z, count, xd, yd, zd, 0.0);
        }
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        spawned = false;
        tick = 0;
    }
}