package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.PrefabService;


public class LoadTemplateStep extends AssemblyStep {
    private final TickDelay delay = newDelay();

    @Override
    protected AssemblyResult tick(AssemblyContext ctx) {
        if (ctx.level == null) return AssemblyResult.FAIL;
        ctx.template = PrefabService.loadPrefab(ctx.level, ctx.templateId);

        delay.start(2);
        if (delay.isWaiting()) return AssemblyResult.WAITING;
        return AssemblyResult.SUCCESS;
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        ctx.template = null;
    }
}