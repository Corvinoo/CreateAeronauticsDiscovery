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
    void builderSetsSource() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.WORLDGEN).build();

        assertEquals(TEMPLATE_ID, ctx.templateId);
        assertEquals(AssemblySource.WORLDGEN, ctx.source);
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
                .maxRetries(99)
                .assemblerPos(anchor)
                .build();

        assertEquals(anchor, ctx.anchor);
        assertEquals(templatePos, ctx.templatePos);
        assertEquals(rotation, ctx.rotationTemplate);
        assertEquals(bounds, ctx.templateBounds);
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
        assertNull(ctx.templateBounds);
        assertEquals(60, ctx.maxRetries);
        assertNull(ctx.template);
        assertNull(ctx.assemblyResult);
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
