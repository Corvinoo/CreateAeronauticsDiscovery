package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.steps.AssemblyStep;

import java.util.List;

public record AssemblyPipeline(String name, List<AssemblyPipelineEntry> steps) {
    public AssemblyResult execute(AssemblyContext ctx, long currentTick) {
        if (currentTick < ctx.nextStepTick) {
            return AssemblyResult.WAITING;
        }

        while (ctx.currentStepIndex < steps.size()) {
            var holder = steps.get(ctx.currentStepIndex);
            AssemblyStep step = holder.step();
            try {
                AssemblyResult result = step.run(ctx);
                if (result == AssemblyResult.FAIL) {
                    CreateAeronauticsDiscovery.LOGGER.debug("[PIPELINE:{}] Step '{}' returned {} for '{}', cleaning up",
                            name, step.getClass().getSimpleName(), result, ctx.templateId);
                    cleanup(ctx, ctx.currentStepIndex);
                    return result;
                }

                ctx.currentStepIndex++;

                long delay = holder.delayInTicks();
                if (delay > 0 && ctx.currentStepIndex < steps.size()) {
                    ctx.nextStepTick = currentTick + delay;
                    CreateAeronauticsDiscovery.LOGGER.debug("[PIPELINE:{}] Step '{}' done, waiting {} ticks before next",
                            name, step.getClass().getSimpleName(), delay);
                    return AssemblyResult.WAITING;
                }
            } catch (Exception exceptionPipeline) {
                CreateAeronauticsDiscovery.LOGGER.error("[PIPELINE:{}] Step '{}' threw exception for '{}', cleaning up",
                        name, step.getClass().getSimpleName(), ctx.templateId, exceptionPipeline);
                try {
                    cleanup(ctx, ctx.currentStepIndex);
                }
                catch (Exception exceptionCleanup) {
                    CreateAeronauticsDiscovery.LOGGER.error("[PIPELINE:{}] Cleaning up '{}' threw exception for '{}', cleaning up",
                            name, step.getClass().getSimpleName(), ctx.templateId, exceptionCleanup);
                }
                return AssemblyResult.FAIL;
            }
        }

        return AssemblyResult.SUCCESS;
    }


    private void cleanup(AssemblyContext ctx, int upToIndex) {
        for (int i = upToIndex; i >= 0; i--) {
            try {
                steps.get(i).step().cleanup(ctx);
            } catch (Exception e) {
                CreateAeronauticsDiscovery.LOGGER.error("[PIPELINE:{}] Cleanup failed for step '{}'",
                        name, steps.get(i).getClass().getSimpleName(), e);
            }
        }
    }

}


