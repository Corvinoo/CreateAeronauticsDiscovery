package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.physics.BuoyancyStabilizationConfig;
import me.corvino.aeronauticsdiscovery.physics.BuoyancyStabilizationManager;

/**
 * Holds a freshly assembled SubLevel stationary until its own lift-gas system
 * produces enough lift to support its weight, then releases it.
 */
public class StabilizeBuoyancyStep extends AssemblyStep {
    @Override
    protected int timeoutTicks() {
        return (int) (BuoyancyStabilizationConfig.DEFAULT.maxHoldSeconds() * 20) + 100;
    }

    @Override
    protected void build(Sequence seq) {
        seq.require(ctx -> {
                    resolveSubLevel(ctx);
                    return true;
                }, "subLevel can't be resolved")
                .run(ctx -> {
                    BuoyancyStabilizationManager.get(ctx.level)
                            .beginStabilizing(resolveSubLevel(ctx), BuoyancyStabilizationConfig.DEFAULT);
                })
                .waitUntil(ctx -> BuoyancyStabilizationManager.get(ctx.level)
                        .pollStabilized(resolveSubLevel(ctx).getUniqueId()));
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        ServerSubLevel subLevel = resolveSubLevel(ctx);
        BuoyancyStabilizationManager.get(ctx.level).cancel(subLevel.getUniqueId());
    }

    private static ServerSubLevel resolveSubLevel(AssemblyContext ctx) {
        if (ctx.assemblyResult == null) throw new IllegalStateException("Critical error, assembly result can't be null!");
        var subLevel = ctx.assemblyResult.subLevel();
        if (!(subLevel instanceof ServerSubLevel)) {
            throw new IllegalStateException("Critical error, sublevel is not server sided!");
        }
        return (ServerSubLevel) subLevel;
    }
}