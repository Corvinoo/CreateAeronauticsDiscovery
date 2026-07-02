package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.Balloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.BalloonMap;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
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
        if (ctx.assemblyResult != null) {
            return ctx.assemblyResult.subLevel() instanceof ServerSubLevel subLevel ? subLevel : null;
        }
        // Recovery after world reload: assemblyResult is not persisted, but
        // subLevelId is. Find the sublevel by iterating the BalloonMap; each
        // balloon's controller position is in plot-grid space
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