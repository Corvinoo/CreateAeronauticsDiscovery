package me.corvino.aeronauticsdiscovery.event;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SpawnPosition {
    private static boolean DEBUG_VISUALIZE = false;

    private final BlockPos pos;
    private final double yawRadians;

    private SpawnPosition(BlockPos pos, double yawRadians) {
        this.pos = pos;
        this.yawRadians = yawRadians;
    }

    public BlockPos pos() { return pos; }

    public double yawRadians() { return yawRadians; }

    public static Builder builder() { return new Builder(); }

    @FunctionalInterface
    public interface Constraint {
        boolean test(BlockPos pos, double yawRadians, ServerLevel level);
    }

    @FunctionalInterface
    public interface RetryStrategy {
        void prepare(Attempt attempt);

        final class Attempt {
            public double angle;
            public int altitude;
            public final int index;
            public final int maxAttempts;
            public final Random random;

            Attempt(double angle, int altitude, int index, int maxAttempts, Random random) {
                this.angle = angle;
                this.altitude = altitude;
                this.index = index;
                this.maxAttempts = maxAttempts;
                this.random = random;
            }
        }
    }

    // direction vector from Aeronautics yaw convention (Z-negated vs Minecraft)
    private static Vec3 dirFromYaw(double yawRadians) {
        return new Vec3(-Math.sin(yawRadians), 0, -Math.cos(yawRadians));
    }

    private static void debugVisualizeRay(ServerLevel level, Vec3 from, Vec3 to, BlockHitResult hit, boolean miss) {
        if (!DEBUG_VISUALIZE) return;
        level.setBlock(BlockPos.containing(from), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        double totalDist = from.distanceTo(to);
        int steps = Math.max(1, (int) (totalDist / 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, (double) i / steps);
            level.setBlock(BlockPos.containing(p), Blocks.LAPIS_BLOCK.defaultBlockState(), 3);
        }
        if (!miss) {
            level.setBlock(BlockPos.containing(hit.getLocation()), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        }
    }

    public static Constraint noObstaclesInFront(int checkBlocks) {
        return (pos, yawRadians, level) -> {
            Vec3 from = Vec3.atBottomCenterOf(pos).add(0, 1, 0);
            Vec3 dir = dirFromYaw(yawRadians);
            Vec3 to = from.add(dir.scale(checkBlocks));

            BlockHitResult hit = level.clip(new ClipContext(
                    from, to,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    (Entity) null
            ));
            boolean miss = hit.getType() == HitResult.Type.MISS;

//            debugVisualizeRay(level, from, to, hit, miss);

            CreateAeronauticsDiscovery.LOGGER.debug(
                    "[OBSTACLE_CHECK] pos={} yaw={} from={} to={} dir=({}, {}) result={} hit={}",
                    pos, Math.toDegrees(yawRadians),
                    from, to,
                    String.format("%.2f", dir.x), String.format("%.2f", dir.z),
                    miss ? "MISS" : "HIT",
                    miss ? "—" : hit.getBlockPos()
            );

            return miss;
        };
    }

    public static final class Builder {
        private BlockPos center;
        private int minAltitude;
        private int maxAltitude = -1;
        private int horizontalDistance;
        private BlockPos facingTarget;
        private int maxAttempts = 10;
        private RetryStrategy strategy = ctx -> ctx.angle = ctx.random.nextDouble() * 2 * Math.PI;
        private final List<Constraint> constraints = new ArrayList<>();

        private Builder() {}

        public Builder center(BlockPos center) { this.center = center; return this; }

        public Builder altitude(int altitude) { this.minAltitude = altitude; this.maxAltitude = -1; return this; }

        public Builder altitudeRange(int min, int max) { this.minAltitude = min; this.maxAltitude = max; return this; }

        public Builder horizontalDistance(int distance) { this.horizontalDistance = distance; return this; }

        public Builder facing(BlockPos target) { this.facingTarget = target; return this; }

        public Builder maxAttempts(int attempts) { this.maxAttempts = attempts; return this; }

        public Builder retryStrategy(RetryStrategy strategy) { this.strategy = strategy; return this; }

        public Builder constrain(Constraint constraint) {
            constraints.add(constraint);
            return this;
        }

        public SpawnPosition build(ServerLevel level, Random random) {
            if (center == null) throw new IllegalStateException("center is required");

            int baseAltitude = maxAltitude > minAltitude
                    ? minAltitude + random.nextInt(maxAltitude - minAltitude)
                    : minAltitude;

            SpawnPosition last = null;

            for (int i = 0; i < maxAttempts; i++) {
                RetryStrategy.Attempt attempt = new RetryStrategy.Attempt(0, baseAltitude, i, maxAttempts, random);
                strategy.prepare(attempt);
                int dx = (int) (Math.cos(attempt.angle) * horizontalDistance);
                int dz = (int) (Math.sin(attempt.angle) * horizontalDistance);

                BlockPos candidate = new BlockPos(
                        center.getX() + dx,
                        attempt.altitude,
                        center.getZ() + dz
                );

                double yaw = 0;
                if (facingTarget != null) {
                    double tdx = facingTarget.getX() - candidate.getX();
                    double tdz = facingTarget.getZ() - candidate.getZ();
                    yaw = -Math.atan2(tdz, tdx) - (Math.PI / 2);
                }

                last = new SpawnPosition(candidate, yaw);

                boolean valid = true;
                for (Constraint c : constraints) {
                    if (!c.test(candidate, yaw, level)) {
                        valid = false;
                        break;
                    }
                }
                if (valid) return last;
            }

            return null;
        }
    }
}
