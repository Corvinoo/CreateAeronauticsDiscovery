package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FindAssemblyStartStepTest {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.parse("aeronauticsdiscovery:test");

    @Test
    void skipsWhenAssemblerPosAlreadySet() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.WORLDGEN)
                .anchor(new BlockPos(0, 150, 0))
                .bounds(new net.minecraft.world.level.levelgen.structure.BoundingBox(0, 140, 0, 10, 160, 10))
                .build();

        assertEquals(AssemblyResult.SUCCESS, new FindAssemblyStartStep().run(ctx));
    }

    @Test
    void returnsFailWhenBoundsAreNull() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER).build();
        assertEquals(AssemblyResult.FAIL, new FindAssemblyStartStep().run(ctx));
    }
}
