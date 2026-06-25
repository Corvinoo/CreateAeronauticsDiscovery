package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.*;

public class LoadTemplateStep implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        assert ctx.level != null;
        if (ctx.template != null) return AssemblyResult.SUCCESS;
        ctx.template = PrefabService.loadPrefab(ctx.level, ctx.templateId);
        return AssemblyResult.SUCCESS;
    }
}
