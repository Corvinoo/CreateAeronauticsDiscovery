package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.assembly.steps.AssemblyStep;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.PIPELINE;

import java.util.List;
import java.util.function.Supplier;

public record AssemblyPipeline(String name, Supplier<List<AssemblyStep>> stepsFactory) {
    public List<AssemblyStep> createSteps() {
        return stepsFactory.get();
    }

    public AssemblyResult execute(AssemblyContext ctx, long currentTick) {
        if (ctx.steps.isEmpty()) {
            ctx.steps = createSteps();
        }
        ctx.currentTick = currentTick;

        while (ctx.currentStepIndex < ctx.steps.size()) {
            AssemblyStep step = ctx.steps.get(ctx.currentStepIndex);

            try {
                AssemblyResult result = step.execute(ctx);

                switch (result) {
                    case WAITING -> { return AssemblyResult.WAITING; }
                    case FAIL -> {
                        ModLog.debug(PIPELINE,
                                "Step '{}' FAILED for template '{}'",
                                name, step.getClass().getSimpleName(), ctx.templateId);
                        cleanup(ctx, ctx.currentStepIndex);
                        return AssemblyResult.FAIL;
                    }
                    case SUCCESS -> ctx.currentStepIndex++;
                }
            } catch (Exception e) {
                ModLog.error(PIPELINE,
                        "Exception occurred in '{}' for template '{}'",
                        name, step.getClass().getSimpleName(), ctx.templateId, e);
                try {
                    cleanup(ctx, ctx.currentStepIndex);
                } catch (Exception ce) {
                    ModLog.error(PIPELINE,
                            "COULD NOT CLEANUP TEMPLATE '{}'", name, ctx.templateId, ce);
                }
                return AssemblyResult.FAIL;
            }
        }

        return AssemblyResult.SUCCESS;
    }

    private void cleanup(AssemblyContext ctx, int upToIndex) {
        for (int i = upToIndex; i >= 0; i--) {
            try {
                ctx.steps.get(i).abort(ctx);
            } catch (Exception e) {
                ModLog.error(PIPELINE,
                        "STEP '{}' CLEANUP FAILED!!",
                        name, ctx.steps.get(i).getClass().getSimpleName(), e);
            }
        }

        // Clean every possible stale state, both tracking and runtime; in this way the state should be consistent between each retrial
        ctx.resetRuntimeState();
    }
}