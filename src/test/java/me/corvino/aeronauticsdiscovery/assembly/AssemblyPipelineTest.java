package me.corvino.aeronauticsdiscovery.assembly;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyPipelineTest {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.parse("aeronauticsdiscovery:test");

    private AssemblyContext makeCtx() {
        return AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.COMMAND).build();
    }

    @Test
    void executeWithNoStepsReturnsSuccess() {
        AssemblyPipeline pipeline = new AssemblyPipeline("empty", List.of());
        assertEquals(AssemblyResult.SUCCESS, pipeline.execute(makeCtx()));
    }

    @Test
    void executeWithAllSuccessfulStepsReturnsSuccess() {
        AssemblyPipeline pipeline = new AssemblyPipeline("all_ok", List.of(
                ctx -> AssemblyResult.SUCCESS,
                ctx -> AssemblyResult.SUCCESS,
                ctx -> AssemblyResult.SUCCESS
        ));
        assertEquals(AssemblyResult.SUCCESS, pipeline.execute(makeCtx()));
    }

    @Test
    void executeStopsAtFirstFail() {
        int[] counter = {0};
        AssemblyPipeline pipeline = new AssemblyPipeline("fail_at_mid", List.of(
                ctx -> { counter[0]++; return AssemblyResult.SUCCESS; },
                ctx -> { counter[0]++; return AssemblyResult.FAIL; },
                ctx -> { counter[0]++; return AssemblyResult.SUCCESS; }
        ));
        assertEquals(AssemblyResult.FAIL, pipeline.execute(makeCtx()));
        assertEquals(2, counter[0]);
    }

    @Test
    void executePassesContextThroughSteps() {
        int[] counter = {0};
        AssemblyPipeline pipeline = new AssemblyPipeline("count", List.of(
                ctx -> { counter[0]++; return AssemblyResult.SUCCESS; },
                ctx -> { counter[0]++; return AssemblyResult.SUCCESS; },
                ctx -> { counter[0]++; return AssemblyResult.SUCCESS; }
        ));
        assertEquals(AssemblyResult.SUCCESS, pipeline.execute(makeCtx()));
        assertEquals(3, counter[0]);
    }

    @Test
    void pipelineNameIsAccessible() {
        AssemblyPipeline pipeline = new AssemblyPipeline("test_name", List.of());
        assertEquals("test_name", pipeline.name());
    }

    @Test
    void stepsListIsAccessible() {
        AssemblyStep step = ctx -> AssemblyResult.SUCCESS;
        AssemblyPipeline pipeline = new AssemblyPipeline("test", List.of(step));
        assertEquals(1, pipeline.steps().size());
        assertSame(step, pipeline.steps().getFirst());
    }
}
