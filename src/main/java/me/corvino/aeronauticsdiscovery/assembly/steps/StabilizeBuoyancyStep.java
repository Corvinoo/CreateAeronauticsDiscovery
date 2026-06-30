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
    protected AssemblyResult tick(AssemblyContext ctx) {
        ServerSubLevel subLevel = resolveSubLevel(ctx);
        if (subLevel == null) return AssemblyResult.FAIL;

        BuoyancyStabilizationManager manager = BuoyancyStabilizationManager.get(ctx.level);

        if (!manager.isTracking(subLevel.getUniqueId())) {
            manager.beginStabilizing(subLevel, BuoyancyStabilizationConfig.DEFAULT);
            return AssemblyResult.WAITING;
        }

        return manager.pollStabilized(subLevel.getUniqueId())
                ? AssemblyResult.SUCCESS
                : AssemblyResult.WAITING;
    }

    @Override
    protected int timeoutTicks() {
        // Comfortably above the manager's own maxHoldSeconds safety valve. The
        // manager's graceful timeout-release should always win first. This is
        // only a true last-resort guard against the manager itself misbehaving.
        return (int) (BuoyancyStabilizationConfig.DEFAULT.maxHoldSeconds() * 20) + 100;
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        ServerSubLevel subLevel = resolveSubLevel(ctx);
        if (subLevel != null) {
            BuoyancyStabilizationManager.get(ctx.level).cancel(subLevel.getUniqueId());
        }
    }

    private static ServerSubLevel resolveSubLevel(AssemblyContext ctx) {
        if (ctx.assemblyResult == null) return null;
        return ctx.assemblyResult.subLevel() instanceof ServerSubLevel subLevel ? subLevel : null;
    }
}