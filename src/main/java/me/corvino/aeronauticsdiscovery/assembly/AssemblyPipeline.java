package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;

import java.util.List;

public record AssemblyPipeline(String name, List<AssemblyStep> steps) {

    public AssemblyResult execute(AssemblyContext ctx) {
        CreateAeronauticsDiscovery.LOGGER.debug("[PIPELINE:{}] Executing for '{}' (source={})", name, ctx.templateId, ctx.source);
        for (int i = 0; i < steps.size(); i++) {
            AssemblyStep step = steps.get(i);
            AssemblyResult result = step.run(ctx);
            if (result != AssemblyResult.SUCCESS) {
                CreateAeronauticsDiscovery.LOGGER.debug("[PIPELINE:{}] Step '{}' returned {} for '{}', cleaning up",
                        name, step.getClass().getSimpleName(), result, ctx.templateId);
                cleanup(ctx, i);
                return result;
            }
        }
        return AssemblyResult.SUCCESS;
    }


    private void cleanup(AssemblyContext ctx, int upToIndex) {
        for (int i = upToIndex; i >= 0; i--) {
            try {
                steps.get(i).cleanup(ctx);
            } catch (Exception e) {
                CreateAeronauticsDiscovery.LOGGER.error("[PIPELINE:{}] Cleanup failed for step '{}'",
                        name, steps.get(i).getClass().getSimpleName(), e);
            }
        }
    }

}
