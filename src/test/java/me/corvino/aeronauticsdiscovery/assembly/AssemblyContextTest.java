package me.corvino.aeronauticsdiscovery.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyContextTest {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.parse("aeronauticsdiscovery:test");

    @Test
    void builderCreatesWorldgenContextWithProximityTrigger() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.WORLDGEN).build();

        assertEquals(TEMPLATE_ID, ctx.templateId);
        assertEquals(AssemblySource.WORLDGEN, ctx.source);
        assertEquals(TriggerType.PROXIMITY, ctx.trigger);
    }

    @Test
    void builderCreatesFlyoverContextWithImmediateTrigger() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER).build();

        assertEquals(AssemblySource.FLYOVER, ctx.source);
        assertEquals(TriggerType.IMMEDIATE, ctx.trigger);
    }

    @Test
    void builderCreatesCommandContextWithImmediateTrigger() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.COMMAND).build();

        assertEquals(AssemblySource.COMMAND, ctx.source);
        assertEquals(TriggerType.IMMEDIATE, ctx.trigger);
    }

    @Test
    void builderSetsAllFields() {
        BlockPos anchor = new BlockPos(10, 20, 30);
        BlockPos templatePos = new BlockPos(40, 50, 60);
        Rotation rotation = Rotation.CLOCKWISE_90;
        BoundingBox bounds = new BoundingBox(0, 0, 0, 10, 10, 10);

        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER)
                .anchor(anchor)
                .templatePos(templatePos)
                .rotationTemplate(rotation)
                .bounds(bounds)
                .activationDistance(200)
                .maxRetries(99)
                .assemblerPos(anchor)
                .build();

        assertEquals(anchor, ctx.anchor);
        assertEquals(templatePos, ctx.templatePos);
        assertEquals(rotation, ctx.rotationTemplate);
        assertEquals(bounds, ctx.bounds);
        assertEquals(200, ctx.activationDistance);
        assertEquals(99, ctx.maxRetries);
        assertEquals(anchor, ctx.assemblerPos);
    }

    @Test
    void builderDefaults() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER).build();

        assertNull(ctx.anchor);
        assertNull(ctx.assemblerPos);
        assertNull(ctx.templatePos);
        assertNull(ctx.rotationTemplate);
        assertNull(ctx.bounds);
        assertEquals(128, ctx.activationDistance);
        assertEquals(60, ctx.maxRetries);
        assertNull(ctx.template);
        assertNull(ctx.assemblyResult);
    }

    @Test
    void explicitTriggerOverridesSourceDefault() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.WORLDGEN)
                .trigger(TriggerType.IMMEDIATE)
                .build();
        assertEquals(TriggerType.IMMEDIATE, ctx.trigger);

        ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER)
                .trigger(TriggerType.PROXIMITY)
                .build();
        assertEquals(TriggerType.PROXIMITY, ctx.trigger);
    }
    
    @Test
    void builderAllowsNullLevel() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.COMMAND).build();
        assertNull(ctx.level);
    }

    @Test
    void injectLevelSetsLevel() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.COMMAND).build();
        assertNull(ctx.level);

        ctx.injectLevel(null);
        assertNull(ctx.level);
    }
}
