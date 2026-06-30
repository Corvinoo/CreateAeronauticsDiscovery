package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.assembly.steps.AssemblyStep;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyPipelineTest {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.parse("aeronauticsdiscovery:test");

    private AssemblyResult executePipeline(AssemblyPipeline pipeline, AssemblyContext ctx) {
        ctx.steps = pipeline.createSteps();
        return pipeline.execute(ctx, 0L);
    }

    private AssemblyContext makeCtx() {
        return AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.COMMAND).build();
    }

    private static AssemblyStep stepOf(Function<AssemblyContext, AssemblyResult> fn) {
        return new AssemblyStep() {
            @Override
            protected AssemblyResult tick(AssemblyContext ctx) {
                return fn.apply(ctx);
            }
        };
    }

    @Test
    void executeWithNoStepsReturnsSuccess() {
        AssemblyPipeline pipeline = new AssemblyPipeline("empty", () -> List.of());
        assertEquals(AssemblyResult.SUCCESS, executePipeline(pipeline, makeCtx()));
    }

    @Test
    void executeWithAllSuccessfulStepsReturnsSuccess() {
        AssemblyPipeline pipeline = new AssemblyPipeline("all_ok", () -> List.of(
                stepOf(ctx -> AssemblyResult.SUCCESS),
                stepOf(ctx -> AssemblyResult.SUCCESS),
                stepOf(ctx -> AssemblyResult.SUCCESS)
        ));
        assertEquals(AssemblyResult.SUCCESS, executePipeline(pipeline, makeCtx()));
    }

    @Test
    void executeStopsAtFirstFail() {
        int[] counter = {0};
        AssemblyPipeline pipeline = new AssemblyPipeline("fail_at_mid", () -> List.of(
                stepOf(ctx -> { counter[0]++; return AssemblyResult.SUCCESS; }),
                stepOf(ctx -> { counter[0]++; return AssemblyResult.FAIL; }),
                stepOf(ctx -> { counter[0]++; return AssemblyResult.SUCCESS; })
        ));
        assertEquals(AssemblyResult.FAIL, executePipeline(pipeline, makeCtx()));
        assertEquals(2, counter[0]);
    }

    @Test
    void executePassesContextThroughSteps() {
        int[] counter = {0};
        AssemblyPipeline pipeline = new AssemblyPipeline("count", () -> List.of(
                stepOf(ctx -> { counter[0]++; return AssemblyResult.SUCCESS; }),
                stepOf(ctx -> { counter[0]++; return AssemblyResult.SUCCESS; }),
                stepOf(ctx -> { counter[0]++; return AssemblyResult.SUCCESS; })
        ));
        assertEquals(AssemblyResult.SUCCESS, executePipeline(pipeline, makeCtx()));
        assertEquals(3, counter[0]);
    }

    @Test
    void pipelineNameIsAccessible() {
        AssemblyPipeline pipeline = new AssemblyPipeline("test_name", () -> List.of());
        assertEquals("test_name", pipeline.name());
    }

    @Test
    void stepsListIsAccessible() {
        AssemblyStep step = stepOf(ctx -> AssemblyResult.SUCCESS);
        AssemblyPipeline pipeline = new AssemblyPipeline("test", () -> List.of(step));
        assertEquals(1, pipeline.createSteps().size());
        assertSame(step, pipeline.createSteps().getFirst());
    }
}
