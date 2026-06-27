package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;

public interface DeferrableStep extends AssemblyStep {

    AssemblyResult begin(AssemblyContext ctx);

    AssemblyResult poll(AssemblyContext ctx);

    /**
     * This method is useful for removing stale state
     */
    default void abort(AssemblyContext ctx) {}

    @Override
    default AssemblyResult run(AssemblyContext ctx) {
        if (ctx.currentStepState == StepState.NOT_STARTED) {
            AssemblyResult result = begin(ctx);
            if (result == AssemblyResult.WAITING) {
                ctx.currentStepState = StepState.IN_PROGRESS;
            }
            return result;
        }
        return poll(ctx);
    }

    @Override
    default void cleanup(AssemblyContext ctx) {
        if (ctx.currentStepState == StepState.IN_PROGRESS) {
            abort(ctx);
        }
        ctx.currentStepState = StepState.NOT_STARTED;
    }
}
