package me.corvino.aeronauticsdiscovery.event;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SpawnPositionTest {

    private static final BlockPos CENTER = new BlockPos(0, 100, 0);
    private static final long SEED = 42L;

    private static final SpawnPosition.RetryStrategy NOOP = attempt -> {};

    // ---------------------------------------------------------------
    // SpawnPosition value
    // ---------------------------------------------------------------

    @Test
    void storesPositionAndYaw() {
        SpawnPosition sp = builder().altitude(100).retryStrategy(NOOP).build(null, new Random(SEED));
        assertNotNull(sp);
        assertEquals(100, sp.pos().getY());
        assertEquals(0.0, sp.yawRadians(), 1e-6);
    }

    // ---------------------------------------------------------------
    // RetryStrategy.Attempt
    // ---------------------------------------------------------------

    @Test
    void attemptStoresAllFields() {
        Random random = new Random(SEED);
        SpawnPosition.RetryStrategy.Attempt attempt =
                new SpawnPosition.RetryStrategy.Attempt(1.5, 200, 3, 10, random);

        assertEquals(1.5, attempt.angle, 1e-12);
        assertEquals(200, attempt.altitude);
        assertEquals(3, attempt.index);
        assertEquals(10, attempt.maxAttempts);
        assertSame(random, attempt.random);
    }

    // ---------------------------------------------------------------
    // RetryStrategy.CHANGE_ANGLE
    // ---------------------------------------------------------------

    @Test
    void changeAngleSetsAngleInRange() {
        Random random = new Random(SEED);
        SpawnPosition.RetryStrategy.Attempt attempt =
                new SpawnPosition.RetryStrategy.Attempt(0, 100, 0, 10, random);

        SpawnPosition.RetryStrategy.CHANGE_ANGLE.prepare(attempt);

        assertTrue(attempt.angle >= 0);
        assertTrue(attempt.angle < 2 * Math.PI);
    }

    @Test
    void changeAnglePreservesOtherFields() {
        Random random = new Random(SEED);
        SpawnPosition.RetryStrategy.Attempt attempt =
                new SpawnPosition.RetryStrategy.Attempt(0, 100, 3, 10, random);

        SpawnPosition.RetryStrategy.CHANGE_ANGLE.prepare(attempt);

        assertEquals(100, attempt.altitude);
        assertEquals(3, attempt.index);
        assertEquals(10, attempt.maxAttempts);
        assertSame(random, attempt.random);
    }

    @Test
    void changeAngleDeterministicWithSeed() {
        double angle1 = extractAngle(new Random(123L));
        double angle2 = extractAngle(new Random(123L));
        double angle3 = extractAngle(new Random(456L));

        assertEquals(angle1, angle2, 1e-12);
        assertNotEquals(angle1, angle3);
    }

    private static double extractAngle(Random random) {
        SpawnPosition.RetryStrategy.Attempt a =
                new SpawnPosition.RetryStrategy.Attempt(0, 100, 0, 10, random);
        SpawnPosition.RetryStrategy.CHANGE_ANGLE.prepare(a);
        return a.angle;
    }

    // ---------------------------------------------------------------
    // Builder — parameter validation
    // ---------------------------------------------------------------

    @Test
    void throwsWithoutCenter() {
        assertThrows(IllegalStateException.class,
                () -> SpawnPosition.builder().retryStrategy(NOOP).build(null, new Random(SEED)));
    }

    @Test
    void fluentApiReturnsSameInstance() {
        SpawnPosition.Builder b = SpawnPosition.builder();
        assertSame(b, b.center(CENTER));
        assertSame(b, b.altitude(100));
        assertSame(b, b.altitudeRange(50, 150));
        assertSame(b, b.horizontalDistance(80));
        assertSame(b, b.facing(new BlockPos(10, 100, 20)));
        assertSame(b, b.maxAttempts(5));
        assertSame(b, b.retryStrategy(NOOP));
        assertSame(b, b.constrain((pos, yaw, lvl) -> true));
    }

    @Test
    void nullStrategyRetriesSamePosition() {
        SpawnPosition sp = SpawnPosition.builder().center(CENTER).build(null, new Random(SEED));
        assertNotNull(sp);
        assertEquals(CENTER.getX(), sp.pos().getX());
        assertEquals(CENTER.getZ(), sp.pos().getZ());
    }

    // ---------------------------------------------------------------
    // Builder — position generation
    // ---------------------------------------------------------------

    @Test
    void fixedAltitude() {
        SpawnPosition sp = builder().altitude(200).retryStrategy(NOOP).build(null, new Random(SEED));
        assertNotNull(sp);
        assertEquals(200, sp.pos().getY());
    }

    @Test
    void altitudeRange() {
        for (int seed = 0; seed < 50; seed++) {
            SpawnPosition sp = builder().altitudeRange(100, 200).retryStrategy(NOOP)
                    .build(null, new Random(seed));
            assertNotNull(sp);
            assertTrue(sp.pos().getY() >= 100 && sp.pos().getY() <= 200,
                    "seed=" + seed + " y=" + sp.pos().getY());
        }
    }

    @Test
    void altitudeRangeMinEqualsMax() {
        SpawnPosition sp = builder().altitudeRange(150, 150).retryStrategy(NOOP)
                .build(null, new Random(SEED));
        assertNotNull(sp);
        assertEquals(150, sp.pos().getY());
    }

    @Test
    void horizontalDistance() {
        SpawnPosition sp = builder().altitude(100).horizontalDistance(80).retryStrategy(NOOP)
                .build(null, new Random(SEED));

        assertNotNull(sp);
        double dx = sp.pos().getX() - CENTER.getX();
        double dz = sp.pos().getZ() - CENTER.getZ();
        assertEquals(80, Math.sqrt(dx * dx + dz * dz), 5);
    }

    // ---------------------------------------------------------------
    // Builder — yaw from facing target
    // ---------------------------------------------------------------

    @Test
    void facingEast() {
        BlockPos east = new BlockPos(CENTER.getX() + 100, CENTER.getY(), CENTER.getZ());
        SpawnPosition sp = builder().facing(east).horizontalDistance(0).retryStrategy(NOOP)
                .build(null, new Random(SEED));
        assertNotNull(sp);
        assertEquals(-Math.PI / 2, sp.yawRadians(), 1e-6);
    }

    @Test
    void facingNorth() {
        BlockPos north = new BlockPos(CENTER.getX(), CENTER.getY(), CENTER.getZ() - 100);
        SpawnPosition sp = builder().facing(north).horizontalDistance(0).retryStrategy(NOOP)
                .build(null, new Random(SEED));
        assertNotNull(sp);
        assertEquals(0.0, sp.yawRadians(), 1e-6);
    }

    @Test
    void facingSouth() {
        BlockPos south = new BlockPos(CENTER.getX(), CENTER.getY(), CENTER.getZ() + 100);
        SpawnPosition sp = builder().facing(south).horizontalDistance(0).retryStrategy(NOOP)
                .build(null, new Random(SEED));
        assertNotNull(sp);
        assertEquals(-Math.PI, sp.yawRadians(), 1e-6);
    }

    @Test
    void facingCenterYieldsZeroYaw() {
        // When candidate == facing target, atan2(0, 0) = 0, so yaw = -0 - PI/2 = -PI/2
        SpawnPosition sp = builder().facing(CENTER).horizontalDistance(0).retryStrategy(NOOP)
                .build(null, new Random(SEED));
        assertNotNull(sp);
        assertEquals(-Math.PI / 2, sp.yawRadians(), 1e-6);
    }

    // ---------------------------------------------------------------
    // RetryStrategy integration
    // ---------------------------------------------------------------

    @Test
    void customStrategyModifiesAltitude() {
        SpawnPosition.RetryStrategy raiseBy10 = attempt -> {
            attempt.altitude = 100 + attempt.index * 10;
        };

        SpawnPosition sp = builder().altitude(200).horizontalDistance(0)
                .maxAttempts(10).retryStrategy(raiseBy10)
                .constrain((pos, yaw, lvl) -> pos.getY() >= 150)
                .build(null, new Random(SEED));

        // index=5 → altitude=150 → first pass
        assertNotNull(sp);
        assertEquals(0, sp.pos().getX());
        assertEquals(150, sp.pos().getY());
        assertEquals(0, sp.pos().getZ());
    }

    // ---------------------------------------------------------------
    // Constraint rejection
    // ---------------------------------------------------------------

    @Test
    void returnsNullWhenNoAttemptSatisfiesConstraint() {
        assertNull(builder().retryStrategy(NOOP).maxAttempts(5)
                .constrain((pos, yaw, lvl) -> false)
                .build(null, new Random(SEED)));
    }

    @Test
    void firstPassingAttemptIsReturned() {
        SpawnPosition sp = builder().altitude(100).horizontalDistance(0)
                .retryStrategy(attempt -> attempt.altitude = 100 + attempt.index)
                .maxAttempts(10)
                .constrain((pos, yaw, lvl) -> pos.getY() >= 103)
                .build(null, new Random(SEED));

        assertNotNull(sp);
        assertEquals(103, sp.pos().getY()); // index=3 → altitude=103
    }

    // ---------------------------------------------------------------
    // noObstaclesInFront constraint (structural only)
    // ---------------------------------------------------------------

    @Test
    void noObstaclesCanBeInstantiated() {
        SpawnPosition.Constraint c = SpawnPosition.noObstaclesInFront(64);
        assertNotNull(c);
    }

    /**
     * NOTE: {@code noObstaclesInFront} invokes {@code level.clip()}, but
     * {@link net.minecraft.server.level.ServerLevel} cannot be loaded in a
     * unit-test environment (its static initializer requires a running server).
     * The constraint's raytrace logic is exercised by integration tests
     * (GameTests) that run on a real Minecraft server.
     */

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static SpawnPosition.Builder builder() {
        return SpawnPosition.builder().center(CENTER);
    }
}
