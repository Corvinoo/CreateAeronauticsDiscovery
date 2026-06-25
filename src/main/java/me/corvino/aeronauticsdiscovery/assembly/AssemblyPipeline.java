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
            AssemblyResult result = step.run(ctx);

            if (result != AssemblyResult.SUCCESS) {
                CreateAeronauticsDiscovery.LOGGER.debug("[PIPELINE:{}] Step '{}' returned {} for '{}'",
                        name, step.getClass().getSimpleName(), result, ctx.templateId);
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
        }

        return AssemblyResult.SUCCESS;
    }
}


