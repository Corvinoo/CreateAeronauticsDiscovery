package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyStep;
import me.corvino.aeronauticsdiscovery.assembly.PrefabReadinessChecks;

public class ReadinessCheckStep implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        if (ctx.bounds == null) {
            return AssemblyResult.DEFER;
        }
        if (PrefabReadinessChecks.firstFailing(ctx.level, ctx.bounds).isPresent()) {
            return AssemblyResult.DEFER;
        }
        return AssemblyResult.SUCCESS;
    }
}
