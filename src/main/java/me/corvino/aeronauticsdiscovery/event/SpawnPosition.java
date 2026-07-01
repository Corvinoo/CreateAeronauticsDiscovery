package me.corvino.aeronauticsdiscovery.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiPredicate;

public final class SpawnPosition {
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

    public static final class Builder {
        private BlockPos center;
        private int minAltitude;
        private int maxAltitude = -1;
        private int horizontalDistance;
        private BlockPos facingTarget;
        private int maxAttempts = 10;
        private final List<Constraint> constraints = new ArrayList<>();

        private Builder() {}

        public Builder center(BlockPos center) { this.center = center; return this; }

        public Builder altitude(int altitude) { this.minAltitude = altitude; this.maxAltitude = -1; return this; }

        public Builder altitudeRange(int min, int max) { this.minAltitude = min; this.maxAltitude = max; return this; }

        public Builder horizontalDistance(int distance) { this.horizontalDistance = distance; return this; }

        public Builder facing(BlockPos target) { this.facingTarget = target; return this; }

        public Builder maxAttempts(int attempts) { this.maxAttempts = attempts; return this; }

        public Builder constrain(Constraint constraint) {
            constraints.add(constraint);
            return this;
        }

        public SpawnPosition build(ServerLevel level, Random random) {
            if (center == null) throw new IllegalStateException("center is required");

            int altitude = maxAltitude > minAltitude
                    ? minAltitude + random.nextInt(maxAltitude - minAltitude)
                    : minAltitude;

            SpawnPosition last = null;

            for (int i = 0; i < maxAttempts; i++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                int dx = (int) (Math.cos(angle) * horizontalDistance);
                int dz = (int) (Math.sin(angle) * horizontalDistance);

                BlockPos candidate = new BlockPos(
                        center.getX() + dx,
                        altitude,
                        center.getZ() + dz
                );

                double yaw = 0;
                if (facingTarget != null) {
                    double theta = Math.atan2(
                            facingTarget.getZ() - candidate.getZ(),
                            facingTarget.getX() - candidate.getX()
                    );
                    yaw = -theta - Math.PI / 2;
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

            return last;
        }
    }
}
