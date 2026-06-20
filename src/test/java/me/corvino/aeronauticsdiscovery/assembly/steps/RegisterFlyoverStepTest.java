package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegisterFlyoverStepTest {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.parse("aeronauticsdiscovery:test");

    @Test
    void skipsForWorldgenSource() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.WORLDGEN).build();
        assertEquals(AssemblyResult.SUCCESS, new RegisterFlyoverStep().run(ctx));
    }

    @Test
    void skipsForCommandSource() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.COMMAND).build();
        assertEquals(AssemblyResult.SUCCESS, new RegisterFlyoverStep().run(ctx));
    }

    @Test
    void returnsSuccessWhenNoAssemblyResult() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER).build();
        assertEquals(AssemblyResult.SUCCESS, new RegisterFlyoverStep().run(ctx));
    }
}
