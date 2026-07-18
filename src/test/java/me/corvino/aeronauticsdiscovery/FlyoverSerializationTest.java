package me.corvino.aeronauticsdiscovery;

import me.corvino.aeronauticsdiscovery.event.FlyoverEventConfig;
import me.corvino.aeronauticsdiscovery.event.manager.FlyoverData;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import me.corvino.aeronauticsdiscovery.physics.PrefabPhysicsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip serialization tests for every flyover-related data type.
 *
 * <p>Each test encodes a value through its Codec, decodes the resulting
 * representation, and asserts every field was preserved.  Both JSON (datapack
 * configs) and NBT (SavedData) formats are exercised.
 *
 * <p>See {@link SerializationTestUtil} for the generic round-trip machinery
 * used here; the same helpers can be re-used by serialization tests for other
 * subsystems.
 */
class FlyoverSerializationTest {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.parse("aeronauticsdiscovery:airplane");
    private static final UUID SUB_LEVEL_ID = UUID.randomUUID();

    // ---------------------------------------------------------------
    // FlyoverData — persisted via NbtOps in FlyoverManager SavedData
    // ---------------------------------------------------------------

    @Nested
    class FlyoverDataTests {

        @Test
        void roundTripFresh() {
            FlyoverData original = FlyoverData.fresh(SUB_LEVEL_ID, TEMPLATE_ID);

            SerializationTestUtil.assertNbtRoundTrip(FlyoverData.CODEC, original, (a, b) -> {
                assertEquals(a.subLevelId(), b.subLevelId());
                assertEquals(0, b.lifeTicks());
                assertEquals(a.templateId(), b.templateId());
            });
        }

        @Test
        void roundTripAfterSomeTicks() {
            FlyoverData original = FlyoverData.fresh(SUB_LEVEL_ID, TEMPLATE_ID);
            for (int i = 0; i < 42; i++) original.incrementTick();

            SerializationTestUtil.assertNbtRoundTrip(FlyoverData.CODEC, original, (a, b) -> {
                assertEquals(a.subLevelId(), b.subLevelId());
                assertEquals(42, b.lifeTicks());
                assertEquals(a.templateId(), b.templateId());
            });
        }

        @Test
        void roundTripMaxLifeTicks() {
            FlyoverData original = new FlyoverData(SUB_LEVEL_ID, Integer.MAX_VALUE, TEMPLATE_ID);

            SerializationTestUtil.assertNbtRoundTrip(FlyoverData.CODEC, original, (a, b) -> {
                assertEquals(a.subLevelId(), b.subLevelId());
                assertEquals(Integer.MAX_VALUE, b.lifeTicks());
                assertEquals(a.templateId(), b.templateId());
            });
        }

        @Test
        void roundTripNegativeLifeTicks() {
            FlyoverData original = new FlyoverData(SUB_LEVEL_ID, -1, TEMPLATE_ID);
            // Negative ticks should round-trip faithfully (even if not a normal state)
            SerializationTestUtil.assertNbtRoundTrip(FlyoverData.CODEC, original, (a, b) -> {
                assertEquals(-1, b.lifeTicks());
            });
        }
    }

    // ---------------------------------------------------------------
    // FlyoverEventConfig — persisted via JsonOps in datapack JSON
    // ---------------------------------------------------------------

    @Nested
    class FlyoverEventConfigTests {

        @Test
        void roundTripDefaults() {
            FlyoverEventConfig original = new FlyoverEventConfig(
                    TEMPLATE_ID, 200, 280, 1, InitialVelocity.NONE, true, List.of());

            SerializationTestUtil.assertJsonRoundTrip(FlyoverEventConfig.CODEC, original, (a, b) -> {
                assertEquals(a.template(), b.template());
                assertEquals(200, b.minAltitude());
                assertEquals(280, b.maxAltitude());
                assertEquals(1, b.weight());
                assertEquals(InitialVelocity.NONE, b.velocity());
                assertTrue(b.randomizeYaw());
            });
        }

        @Test
        void roundTripAllExplicit() {
            FlyoverEventConfig original = new FlyoverEventConfig(
                    ResourceLocation.parse("aeronauticsdiscovery:test"),
                    50, 400, 10,
                    new InitialVelocity(new Vec3(0.5, 0.0, 0.0), new Vec3(0.0, 0.1, 0.0), true),
                    false, List.of());

            SerializationTestUtil.assertJsonRoundTrip(FlyoverEventConfig.CODEC, original, (a, b) -> {
                assertEquals(a.template(), b.template());
                assertEquals(50, b.minAltitude());
                assertEquals(400, b.maxAltitude());
                assertEquals(10, b.weight());
                assertVec3(0.5, 0.0, 0.0, b.velocity().linear());
                assertVec3(0.0, 0.1, 0.0, b.velocity().angular());
                assertTrue(b.velocity().impulse());
                assertFalse(b.randomizeYaw());
            });
        }

        @Test
        void roundTripMinAltitudeOnly() {
            FlyoverEventConfig original = new FlyoverEventConfig(
                    TEMPLATE_ID, 500, 280, 1, InitialVelocity.NONE, true, List.of());

            SerializationTestUtil.assertJsonRoundTrip(FlyoverEventConfig.CODEC, original, (a, b) -> {
                assertEquals(500, b.minAltitude());
                assertEquals(280, b.maxAltitude());
            });
        }

        @Test
        void roundTripLargeWeight() {
            FlyoverEventConfig original = new FlyoverEventConfig(
                    TEMPLATE_ID, 200, 280, Integer.MAX_VALUE, InitialVelocity.NONE, true, List.of());

            SerializationTestUtil.assertJsonRoundTrip(FlyoverEventConfig.CODEC, original, (a, b) -> {
                assertEquals(Integer.MAX_VALUE, b.weight());
            });
        }

        @Test
        void roundTripWithInitialVelocityOnly() {
            FlyoverEventConfig original = new FlyoverEventConfig(
                    TEMPLATE_ID, 200, 280, 1,
                    new InitialVelocity(new Vec3(1.0, 2.0, 3.0), new Vec3(0.1, 0.2, 0.3), true),
                    true, List.of());

            SerializationTestUtil.assertJsonRoundTrip(FlyoverEventConfig.CODEC, original, (a, b) -> {
                assertVec3(1.0, 2.0, 3.0, b.velocity().linear());
                assertVec3(0.1, 0.2, 0.3, b.velocity().angular());
                assertTrue(b.velocity().impulse());
            });
        }

        @Test
        void roundTripVelocityDefaults() {
            // Velocity defaults (Vec3.ZERO, impulse=false) should survive round-trip as NONE
            FlyoverEventConfig original = new FlyoverEventConfig(
                    TEMPLATE_ID, 200, 280, 1,
                    new InitialVelocity(Vec3.ZERO, Vec3.ZERO, false), true, List.of());

            SerializationTestUtil.assertJsonRoundTrip(FlyoverEventConfig.CODEC, original, (a, b) -> {
                assertEquals(InitialVelocity.NONE, b.velocity());
            });
        }
    }

    // ---------------------------------------------------------------
    // PrefabPhysicsConfig - persisted via JsonOps in datapack JSON
    // ---------------------------------------------------------------

    @Nested
    class PrefabPhysicsConfigTests {

        @Test
        void roundTripSimple() {
            PrefabPhysicsConfig original = new PrefabPhysicsConfig(
                    TEMPLATE_ID,
                    new InitialVelocity(new Vec3(1.0, 0.0, 0.0), Vec3.ZERO, false));

            SerializationTestUtil.assertJsonRoundTrip(
                    PrefabPhysicsConfig.CODEC.codec(), original,
                    (a, b) -> {
                        assertEquals(a.template(), b.template());
                        assertVec3(1.0, 0.0, 0.0, b.initialVelocity().linear());
                        assertVec3(0.0, 0.0, 0.0, b.initialVelocity().angular());
                        assertFalse(b.initialVelocity().impulse());
                    });
        }

        @Test
        void roundTripFullVelocity() {
            PrefabPhysicsConfig original = new PrefabPhysicsConfig(
                    ResourceLocation.parse("aeronauticsdiscovery:balloon"),
                    new InitialVelocity(new Vec3(0.0, 2.5, 1.0), new Vec3(0.1, 0.0, 0.05), true));

            SerializationTestUtil.assertJsonRoundTrip(
                    PrefabPhysicsConfig.CODEC.codec(), original,
                    (a, b) -> {
                        assertEquals(a.template(), b.template());
                        assertVec3(0.0, 2.5, 1.0, b.initialVelocity().linear());
                        assertVec3(0.1, 0.0, 0.05, b.initialVelocity().angular());
                        assertTrue(b.initialVelocity().impulse());
                    });
        }

        @Test
        void roundTripZeroVelocity() {
            PrefabPhysicsConfig original = new PrefabPhysicsConfig(
                    TEMPLATE_ID, InitialVelocity.NONE);

            SerializationTestUtil.assertJsonRoundTrip(
                    PrefabPhysicsConfig.CODEC.codec(), original,
                    (a, b) -> {
                        assertEquals(a.template(), b.template());
                        assertEquals(InitialVelocity.NONE, b.initialVelocity());
                    });
        }
    }

    // ---------------------------------------------------------------
    // InitialVelocity - persisted as nested codec in both configs
    // ---------------------------------------------------------------

    @Nested
    class InitialVelocityTests {

        @Test
        void roundTripZero() {
            InitialVelocity original = InitialVelocity.NONE;

            SerializationTestUtil.assertJsonRoundTrip(InitialVelocity.CODEC.codec(), original, (a, b) -> {
                assertVec3(0, 0, 0, b.linear());
                assertVec3(0, 0, 0, b.angular());
                assertFalse(b.impulse());
            });
        }

        @Test
        void roundTripFull() {
            InitialVelocity original = new InitialVelocity(
                    new Vec3(1.5, -2.0, 3.0), new Vec3(0.1, 0.2, 0.3), true);

            SerializationTestUtil.assertJsonRoundTrip(InitialVelocity.CODEC.codec(), original, (a, b) -> {
                assertVec3(1.5, -2.0, 3.0, b.linear());
                assertVec3(0.1, 0.2, 0.3, b.angular());
                assertTrue(b.impulse());
            });
        }

        @Test
        void roundTripLinearOnly() {
            InitialVelocity original = new InitialVelocity(
                    new Vec3(10.0, 20.0, 30.0), Vec3.ZERO, false);

            SerializationTestUtil.assertJsonRoundTrip(InitialVelocity.CODEC.codec(), original, (a, b) -> {
                assertVec3(10.0, 20.0, 30.0, b.linear());
                assertVec3(0, 0, 0, b.angular());
                assertFalse(b.impulse());
            });
        }

        @Test
        void roundTripAngularOnly() {
            InitialVelocity original = new InitialVelocity(
                    Vec3.ZERO, new Vec3(0.5, 0.0, -0.5), true);

            SerializationTestUtil.assertJsonRoundTrip(InitialVelocity.CODEC.codec(), original, (a, b) -> {
                assertVec3(0, 0, 0, b.linear());
                assertVec3(0.5, 0.0, -0.5, b.angular());
                assertTrue(b.impulse());
            });
        }

        @Test
        void roundTripNegativeValues() {
            InitialVelocity original = new InitialVelocity(
                    new Vec3(-1.0, -2.5, -3.0), new Vec3(-0.1, -0.2, -0.3), true);

            SerializationTestUtil.assertJsonRoundTrip(InitialVelocity.CODEC.codec(), original, (a, b) -> {
                assertVec3(-1.0, -2.5, -3.0, b.linear());
                assertVec3(-0.1, -0.2, -0.3, b.angular());
                assertTrue(b.impulse());
            });
        }

        @Test
        void roundTripPrecision() {
            InitialVelocity original = new InitialVelocity(
                    new Vec3(0.123456789, 1.0 / 3.0, Math.PI), Vec3.ZERO, false);

            SerializationTestUtil.assertJsonRoundTrip(InitialVelocity.CODEC.codec(), original, (a, b) -> {
                assertEquals(0.123456789, b.linear().x(), 1e-8);
                assertEquals(1.0 / 3.0, b.linear().y(), 1e-8);
                assertEquals(Math.PI, b.linear().z(), 1e-8);
            });
        }
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static void assertVec3(double x, double y, double z, Vec3 actual) {
        assertEquals(x, actual.x(), 1e-6);
        assertEquals(y, actual.y(), 1e-6);
        assertEquals(z, actual.z(), 1e-6);
    }
}
