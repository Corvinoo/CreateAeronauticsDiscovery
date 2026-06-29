package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.*;


public class LoadTemplateStep extends AssemblyStep {
    private final TickDelay delay = newDelay();

    @Override
    protected AssemblyResult tick(AssemblyContext ctx) {
        delay.start(2);
        if (delay.isWaiting()) return AssemblyResult.WAITING;

        if (ctx.level == null) return AssemblyResult.FAIL;
        ctx.template = PrefabService.loadPrefab(ctx.level, ctx.templateId);
        return AssemblyResult.SUCCESS;
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        ctx.template = null;
    }
}