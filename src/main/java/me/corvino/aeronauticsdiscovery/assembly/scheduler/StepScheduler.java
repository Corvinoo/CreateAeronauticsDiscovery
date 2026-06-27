package me.corvino.aeronauticsdiscovery.assembly.scheduler;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;

public class StepScheduler {
    private static final long NOT_SCHEDULED = -1;
    private final String key;

    public StepScheduler(Class<?> ownerStep) {
        this.key = "scheduler:" + ownerStep.getName();
    }

    public void scheduleAfter(AssemblyContext ctx, long ticks) {
        ctx.stepData.put(key, ctx.currentTick + ticks);
    }

    public boolean isReady(AssemblyContext ctx) {
        long readyAt = (long) ctx.stepData.getOrDefault(key, NOT_SCHEDULED);
        return readyAt == NOT_SCHEDULED || ctx.currentTick >= readyAt;
    }

    public void reset(AssemblyContext ctx) {
        ctx.stepData.remove(key);
    }
}