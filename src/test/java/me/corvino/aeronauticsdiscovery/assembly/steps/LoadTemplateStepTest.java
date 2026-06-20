package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoadTemplateStepTest {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.parse("aeronauticsdiscovery:test");

    @Test
    void skipsWhenTemplateAlreadyLoaded() {
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.COMMAND).build();
        ctx.template = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
        assertEquals(AssemblyResult.SUCCESS, new LoadTemplateStep().run(ctx));
    }
}
