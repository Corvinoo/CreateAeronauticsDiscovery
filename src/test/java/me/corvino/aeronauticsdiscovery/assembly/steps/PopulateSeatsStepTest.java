package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PopulateSeatsStepTest {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.parse("aeronauticsdiscovery:test");

    @Test
    void returnsSuccessWhenNoSeats() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.COMMAND).build();
        assertEquals(AssemblyResult.SUCCESS, new PopulateSeatsStep().run(ctx));
    }

    @Test
    void returnsSuccessWhenNoAssemblyResult() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.COMMAND).build();
        ctx.worldSeatPositions.add(new net.minecraft.core.BlockPos(0, 150, 0));
        assertEquals(AssemblyResult.SUCCESS, new PopulateSeatsStep().run(ctx));
    }
}
