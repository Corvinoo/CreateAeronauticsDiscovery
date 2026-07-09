package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.Balloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.BalloonMap;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.physics.BuoyancyStabilizationConfig;
import me.corvino.aeronauticsdiscovery.physics.BuoyancyStabilizationManager;

/**
 * Holds a freshly assembled SubLevel stationary until its own lift-gas system
 * produces enough lift to support its weight, then releases it.
 */
public class StabilizeBuoyancyStep extends AssemblyStep {

    private ServerSubLevel subLevel;

    @Override
    protected void build(Sequence seq) {
        seq
                .run(ctx -> {
                    subLevel = resolveSubLevel(ctx);
                    if (subLevel == null) return; //to avoid NPE on assembly fail
                    BuoyancyStabilizationManager manager = BuoyancyStabilizationManager.get(ctx.level);
                    if (!manager.isTracking(subLevel.getUniqueId())) {
                        manager.beginStabilizing(subLevel, BuoyancyStabilizationConfig.DEFAULT);
                    }
                })
                .waitUntil(ctx -> BuoyancyStabilizationManager.get(ctx.level)
                        .pollStabilized(subLevel.getUniqueId()));
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
        ServerSubLevel resolved = subLevel != null ? subLevel : resolveSubLevel(ctx);
        if (resolved != null) {
            BuoyancyStabilizationManager.get(ctx.level).cancel(resolved.getUniqueId());
        }
    }

    private static ServerSubLevel resolveSubLevel(AssemblyContext ctx) {
        if (ctx.assemblyResult != null) {
            return ctx.assemblyResult.subLevel() instanceof ServerSubLevel subLevel ? subLevel : null;
        }
        if (ctx.subLevelId != null && ctx.level != null) {
            for (Balloon balloon : BalloonMap.MAP.get(ctx.level).getBalloons()) {
                SubLevel subLevel = Sable.HELPER.getContaining(ctx.level, balloon.getControllerPos());
                if (subLevel != null && subLevel.getUniqueId().equals(ctx.subLevelId)) {
                    return subLevel instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
                }
            }
        }
        return null;
    }
}