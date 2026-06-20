package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RotateBodyStepTest {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.parse("aeronauticsdiscovery:test");

    @Test
    void skipsWhenYawIsZero() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER)
                .yawRadians(0.0)
                .build();
        assertEquals(AssemblyResult.SUCCESS, new RotateBodyStep().run(ctx));
    }

    @Test
    void skipsWhenAssemblyResultIsNull() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER)
                .yawRadians(1.57)
                .build();
        assertEquals(AssemblyResult.SUCCESS, new RotateBodyStep().run(ctx));
    }

    @Test
    void skipsWhenBoundsAreNull() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.FLYOVER)
                .yawRadians(1.57)
                .build();
        assertEquals(AssemblyResult.SUCCESS, new RotateBodyStep().run(ctx));
    }
}
