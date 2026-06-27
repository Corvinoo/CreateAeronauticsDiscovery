package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.*;
import me.corvino.aeronauticsdiscovery.assembly.scheduler.StepScheduler;

public class LoadTemplateStep implements DeferrableStep {
    private final StepScheduler scheduler = new StepScheduler(LoadTemplateStep.class);


    @Override
    public AssemblyResult begin(AssemblyContext ctx) {
        scheduler.scheduleAfter(ctx, 2);
        return AssemblyResult.WAITING;
    }

    @Override
    public AssemblyResult poll(AssemblyContext ctx) {
        if (!scheduler.isReady(ctx)) return AssemblyResult.WAITING;

        assert ctx.level != null;
        ctx.template = PrefabService.loadPrefab(ctx.level, ctx.templateId);
        return AssemblyResult.SUCCESS;
    }

    @Override
    public void abort(AssemblyContext ctx) {
        scheduler.reset(ctx);
        ctx.template = null;
    }
}
