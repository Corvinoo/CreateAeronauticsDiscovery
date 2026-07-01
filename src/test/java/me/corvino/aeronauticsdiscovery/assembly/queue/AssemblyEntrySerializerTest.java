package me.corvino.aeronauticsdiscovery.assembly.queue;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyPipeline;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import me.corvino.aeronauticsdiscovery.assembly.Pipelines;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for {@link AssemblyEntrySerializer}, the manual NBT
 * serializer responsible for persisting {@link AssemblyQueue.Entry} objects
 * in the {@code aeronauticsdiscovery_assembly_queue} SavedData.
 *
 * <p>These tests verify that every field the serializer writes is correctly
 * read back, and that fields the serializer deliberately skips
 * ({@code velocityOverride}, runtime-only fields) are in their default
 * state after deserialization.
 */
class AssemblyEntrySerializerTest {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.parse("aeronauticsdiscovery:airplane");
    private static final AssemblyPipeline PIPELINE = Pipelines.byName("flyover");

    /**
     * Build a fully populated {@link AssemblyContext} with every optional
     * field set.
     */
    private static AssemblyContext fullContext() {
        BlockPos anchor = new BlockPos(100, 64, 200);
        BlockPos tplPos = new BlockPos(10, 20, 30);
        BlockPos asmPos = new BlockPos(101, 64, 201);
        BoundingBox bounds = new BoundingBox(0, 40, 0, 16, 60, 16);

        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER)
                .anchor(anchor)
                .templatePos(tplPos)
                .rotationTemplate(Rotation.CLOCKWISE_90)
                .bounds(bounds)
                .maxRetries(99)
                .assemblerPos(asmPos)
                .setYaw(1.57079632679)
                .setName("test_flyover")
                .registerFlyover()
                .overrideVelocity(new InitialVelocity(new Vec3(0.5, 0, 0), Vec3.ZERO, false))
                .build();

        ctx.entryId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        ctx.currentStepIndex = 3;
        ctx.steps = PIPELINE.createSteps();

        return ctx;
    }

    /**
     * Build a {@link AssemblyContext} with all optional fields set to null.
     */
    private static AssemblyContext minimalContext() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.COMMAND).build();
        ctx.steps = PIPELINE.createSteps();
        return ctx;
    }

    // ---------------------------------------------------------------
    // Full round-trip — every serialized field populated
    // ---------------------------------------------------------------

    @Nested
    class FullRoundTrip {

        @Test
        void roundTripsAllFields() {
            AssemblyContext originalCtx = fullContext();
            AssemblyQueue.Entry original = new AssemblyQueue.Entry(TEMPLATE_ID, PIPELINE, originalCtx, 7);

            CompoundTag tag = AssemblyEntrySerializer.save(original);
            AssemblyQueue.Entry loaded = AssemblyEntrySerializer.load(tag)
                    .orElseThrow(() -> new AssertionError("Load returned empty"));

            // Entry-level fields
            assertEquals(original.templateId(), loaded.templateId());
            assertEquals("flyover", loaded.pipeline().name());
            assertEquals(7, loaded.retryCount());

            // Context — serialized fields
            AssemblyContext ctx = loaded.context();
            assertEquals(TEMPLATE_ID, ctx.templateId);
            assertEquals(AssemblySource.FLYOVER, ctx.source);
            assertEquals(new BlockPos(100, 64, 200), ctx.anchor);
            assertEquals(new BlockPos(101, 64, 201), ctx.assemblerPos);
            assertEquals(new BlockPos(10, 20, 30), ctx.templatePos);
            assertEquals(Rotation.CLOCKWISE_90, ctx.rotationTemplate);
            assertBoundsEquals(new BoundingBox(0, 40, 0, 16, 60, 16), ctx.bounds);
            assertEquals(99, ctx.maxRetries);
            assertEquals(1.57079632679, ctx.yawRadians, 1e-8);
            assertEquals("test_flyover", ctx.subLevelName);
            assertTrue(ctx.registerAsFlyover);
            assertEquals(UUID.fromString("12345678-1234-1234-1234-123456789abc"), ctx.entryId);
            assertEquals(3, ctx.currentStepIndex);

            // Steps should be recreated
            assertNotNull(ctx.steps);
            assertEquals(PIPELINE.createSteps().size(), ctx.steps.size());
        }

        @Test
        void nonSerializedFieldsAreAtDefaults() {
            AssemblyContext originalCtx = fullContext();
            AssemblyQueue.Entry original = new AssemblyQueue.Entry(TEMPLATE_ID, PIPELINE, originalCtx, 7);

            CompoundTag tag = AssemblyEntrySerializer.save(original);
            AssemblyQueue.Entry loaded = AssemblyEntrySerializer.load(tag)
                    .orElseThrow(() -> new AssertionError("Load returned empty"));

            AssemblyContext ctx = loaded.context();

            // runtime-only — never serialized
            assertNull(ctx.level);
            assertNull(ctx.template);
            assertNull(ctx.assemblyResult);
            assertFalse(ctx.seatsPopulated);
            assertEquals(0, ctx.currentTick);

            // velocityOverride is intentionally NOT serialized
            assertNull(ctx.velocityOverride);
        }
    }

    // ---------------------------------------------------------------
    // Minimal round-trip — all optional fields null
    // ---------------------------------------------------------------

    @Nested
    class MinimalRoundTrip {

        @Test
        void roundTripsMinimal() {
            AssemblyContext originalCtx = minimalContext();
            AssemblyQueue.Entry original = new AssemblyQueue.Entry(TEMPLATE_ID, PIPELINE, originalCtx, 0);

            CompoundTag tag = AssemblyEntrySerializer.save(original);
            AssemblyQueue.Entry loaded = AssemblyEntrySerializer.load(tag)
                    .orElseThrow(() -> new AssertionError("Load returned empty"));

            assertEquals(0, loaded.retryCount());
            AssemblyContext ctx = loaded.context();

            assertEquals(TEMPLATE_ID, ctx.templateId);
            assertEquals(AssemblySource.COMMAND, ctx.source);
            assertNull(ctx.anchor);
            assertNull(ctx.assemblerPos);
            assertNull(ctx.templatePos);
            assertNull(ctx.rotationTemplate);
            assertNull(ctx.bounds);
            assertEquals(60, ctx.maxRetries);
            assertEquals(0.0, ctx.yawRadians, 1e-8);
            assertNull(ctx.subLevelName);
            assertFalse(ctx.registerAsFlyover);
            assertEquals(0, ctx.currentStepIndex);

            assertNotNull(ctx.steps);
        }
    }

    // ---------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------

    @Nested
    class EdgeCases {

        @Test
        void retryCountPreserved() {
            AssemblyContext ctx = minimalContext();
            AssemblyQueue.Entry original = new AssemblyQueue.Entry(TEMPLATE_ID, PIPELINE, ctx, Integer.MAX_VALUE);

            CompoundTag tag = AssemblyEntrySerializer.save(original);
            AssemblyQueue.Entry loaded = AssemblyEntrySerializer.load(tag)
                    .orElseThrow(() -> new AssertionError("Load returned empty"));

            assertEquals(Integer.MAX_VALUE, loaded.retryCount());
        }

        @Test
        void negativeYaw() {
            AssemblyContext originalCtx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER)
                    .setYaw(-3.14159)
                    .build();
            originalCtx.steps = PIPELINE.createSteps();
            AssemblyQueue.Entry original = new AssemblyQueue.Entry(TEMPLATE_ID, PIPELINE, originalCtx, 0);

            CompoundTag tag = AssemblyEntrySerializer.save(original);
            AssemblyQueue.Entry loaded = AssemblyEntrySerializer.load(tag)
                    .orElseThrow(() -> new AssertionError("Load returned empty"));

            assertEquals(-3.14159, loaded.context().yawRadians, 1e-8);
        }

        @Test
        void maxRetriesZero() {
            AssemblyContext originalCtx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER)
                    .maxRetries(0)
                    .build();
            originalCtx.steps = PIPELINE.createSteps();
            AssemblyQueue.Entry original = new AssemblyQueue.Entry(TEMPLATE_ID, PIPELINE, originalCtx, 0);

            CompoundTag tag = AssemblyEntrySerializer.save(original);
            AssemblyQueue.Entry loaded = AssemblyEntrySerializer.load(tag)
                    .orElseThrow(() -> new AssertionError("Load returned empty"));

            assertEquals(0, loaded.context().maxRetries);
        }

        @Test
        void worldgenEntry() {
            AssemblyContext originalCtx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.WORLDGEN).build();
            originalCtx.steps = Pipelines.byName("worldgen").createSteps();
            AssemblyQueue.Entry original = new AssemblyQueue.Entry(TEMPLATE_ID, Pipelines.byName("worldgen"), originalCtx, 0);

            CompoundTag tag = AssemblyEntrySerializer.save(original);
            AssemblyQueue.Entry loaded = AssemblyEntrySerializer.load(tag)
                    .orElseThrow(() -> new AssertionError("Load returned empty"));

            assertEquals(AssemblySource.WORLDGEN, loaded.context().source);
            assertEquals("worldgen", loaded.pipeline().name());
        }

        @Test
        void commandEntry() {
            AssemblyContext originalCtx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.COMMAND).build();
            originalCtx.steps = Pipelines.byName("command").createSteps();
            AssemblyQueue.Entry original = new AssemblyQueue.Entry(TEMPLATE_ID, Pipelines.byName("command"), originalCtx, 0);

            CompoundTag tag = AssemblyEntrySerializer.save(original);
            AssemblyQueue.Entry loaded = AssemblyEntrySerializer.load(tag)
                    .orElseThrow(() -> new AssertionError("Load returned empty"));

            assertEquals(AssemblySource.COMMAND, loaded.context().source);
            assertEquals("command", loaded.pipeline().name());
        }

        @Test
        void uniqueEntryIdsAfterDeserialization() {
            AssemblyContext ctx1 = minimalContext();
            ctx1.entryId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            AssemblyContext ctx2 = minimalContext();
            ctx2.entryId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

            AssemblyQueue.Entry e1 = new AssemblyQueue.Entry(TEMPLATE_ID, PIPELINE, ctx1, 0);
            AssemblyQueue.Entry e2 = new AssemblyQueue.Entry(TEMPLATE_ID, PIPELINE, ctx2, 0);

            CompoundTag t1 = AssemblyEntrySerializer.save(e1);
            CompoundTag t2 = AssemblyEntrySerializer.save(e2);
            AssemblyQueue.Entry l1 = AssemblyEntrySerializer.load(t1).orElseThrow();
            AssemblyQueue.Entry l2 = AssemblyEntrySerializer.load(t2).orElseThrow();

            assertNotEquals(l1.context().entryId, l2.context().entryId);
        }

        @Test
        void boundsAllNullReturnsEmptyBoundingBox() {
            AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER).build();
            ctx.steps = PIPELINE.createSteps();
            AssemblyQueue.Entry original = new AssemblyQueue.Entry(TEMPLATE_ID, PIPELINE, ctx, 0);

            CompoundTag tag = AssemblyEntrySerializer.save(original);
            AssemblyQueue.Entry loaded = AssemblyEntrySerializer.load(tag)
                    .orElseThrow(() -> new AssertionError("Load returned empty"));

            assertNull(loaded.context().bounds);
        }

        @Test
        void rotationNotSavedWhenNull() {
            AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER).build();
            assertNull(ctx.rotationTemplate);
            ctx.steps = PIPELINE.createSteps();
            AssemblyQueue.Entry original = new AssemblyQueue.Entry(TEMPLATE_ID, PIPELINE, ctx, 0);

            CompoundTag tag = AssemblyEntrySerializer.save(original);
            AssemblyQueue.Entry loaded = AssemblyEntrySerializer.load(tag)
                    .orElseThrow(() -> new AssertionError("Load returned empty"));

            assertNull(loaded.context().rotationTemplate);
        }
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static void assertBoundsEquals(BoundingBox expected, BoundingBox actual) {
        assertNotNull(actual, "bounds should not be null");
        assertEquals(expected.minX(), actual.minX());
        assertEquals(expected.minY(), actual.minY());
        assertEquals(expected.minZ(), actual.minZ());
        assertEquals(expected.maxX(), actual.maxX());
        assertEquals(expected.maxY(), actual.maxY());
        assertEquals(expected.maxZ(), actual.maxZ());
    }
}
