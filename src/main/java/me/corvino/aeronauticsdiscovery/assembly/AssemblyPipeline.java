package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;

import java.util.List;

public record AssemblyPipeline(String name, List<AssemblyStep> steps) {

    public AssemblyResult execute(AssemblyContext ctx) {
        CreateAeronauticsDiscovery.LOGGER.debug("[PIPELINE:{}] Executing for '{}' (source={})", name, ctx.templateId, ctx.source);
        for (AssemblyStep step : steps) {
            AssemblyResult result = step.run(ctx);
            if (result != AssemblyResult.SUCCESS) {
                CreateAeronauticsDiscovery.LOGGER.debug("[PIPELINE:{}] Step '{}' returned {} for '{}'",
                        name, step.getClass().getSimpleName(), result, ctx.templateId);
                return result;
            }
        }
        return AssemblyResult.SUCCESS;
    }
}
