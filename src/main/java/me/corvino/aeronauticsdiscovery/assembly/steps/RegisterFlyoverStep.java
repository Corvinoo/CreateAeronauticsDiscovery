package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyStep;
import me.corvino.aeronauticsdiscovery.event.FlyoverManager;

public class RegisterFlyoverStep implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        if (ctx.source != AssemblySource.FLYOVER || ctx.assemblyResult == null) {
            return AssemblyResult.SUCCESS;
        }

        FlyoverManager.get(ctx.level).addFlyover(ctx.assemblyResult.subLevel(), ctx.templateId);
        return AssemblyResult.SUCCESS;
    }
}
