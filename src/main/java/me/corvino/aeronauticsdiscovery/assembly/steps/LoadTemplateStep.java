package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.PrefabService;


public class LoadTemplateStep extends AssemblyStep {
    @Override
    protected void build(Sequence seq) {
        seq.require(ctx -> ctx.level != null, "level not found")
                .run(ctx -> {
                    assert ctx.level != null;
                    ctx.template = PrefabService.loadPrefab(ctx.level, ctx.templateId);
                    ctx.templateSize = ctx.template != null ? ctx.template.getSize() : null;
                })
                .delay(2);
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        ctx.template = null;
    }
}