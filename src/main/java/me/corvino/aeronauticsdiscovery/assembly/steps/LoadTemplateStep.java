package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyStep;
import me.corvino.aeronauticsdiscovery.assembly.PrefabService;

public class LoadTemplateStep implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        if (ctx.template != null) return AssemblyResult.SUCCESS;
        ctx.template = PrefabService.loadPrefab(ctx.level, ctx.templateId);
        return AssemblyResult.SUCCESS;
    }
}
