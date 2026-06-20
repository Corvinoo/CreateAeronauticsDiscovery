package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlaceBlocksStepTest {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.parse("aeronauticsdiscovery:test");

    @Test
    void skipsWhenAssemblerPosAlreadySet() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.WORLDGEN)
                .assemblerPos(new BlockPos(0, 150, 0))
                .build();

        assertEquals(AssemblyResult.SUCCESS, new PlaceBlocksStep().run(ctx));
    }
}
