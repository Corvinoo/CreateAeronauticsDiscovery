package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.steps.AssemblyStep;
import me.corvino.aeronauticsdiscovery.assembly.steps.StepState;

import java.util.List;

public record AssemblyPipeline(String name, List<AssemblyStep> steps) {

    public AssemblyResult execute(AssemblyContext ctx, long currentTick) {
        ctx.currentTick = currentTick;

        while (ctx.currentStepIndex < steps.size()) {
            AssemblyStep step = steps.get(ctx.currentStepIndex);

            try {
                AssemblyResult result = step.run(ctx);

                switch (result) {
                    case WAITING -> { return AssemblyResult.WAITING; }
                    case FAIL -> {
                        CreateAeronauticsDiscovery.LOGGER.debug(
                                "[PIPELINE:{}] Step '{}' FAIL per '{}'",
                                name, step.getClass().getSimpleName(), ctx.templateId);
                        cleanup(ctx, ctx.currentStepIndex);
                        return AssemblyResult.FAIL;
                    }
                    case SUCCESS -> {
                        ctx.currentStepState = StepState.NOT_STARTED;
                        ctx.currentStepIndex++;
                    }
                }
            } catch (Exception e) {
                CreateAeronauticsDiscovery.LOGGER.error(
                        "[PIPELINE:{}] Step '{}' exception per '{}'",
                        name, step.getClass().getSimpleName(), ctx.templateId, e);
                try {
                    cleanup(ctx, ctx.currentStepIndex);
                } catch (Exception ce) {
                    CreateAeronauticsDiscovery.LOGGER.error(
                            "[PIPELINE:{}] Cleanup exception per '{}'", name, ctx.templateId, ce);
                }
                return AssemblyResult.FAIL;
            }
        }

        return AssemblyResult.SUCCESS;
    }

    private void cleanup(AssemblyContext ctx, int upToIndex) {
        for (int i = upToIndex; i >= 0; i--) {
            try {
                steps.get(i).cleanup(ctx);
            } catch (Exception e) {
                CreateAeronauticsDiscovery.LOGGER.error(
                        "[PIPELINE:{}] Cleanup step '{}' fallito",
                        name, steps.get(i).getClass().getSimpleName(), e);
            }
        }
    }
}