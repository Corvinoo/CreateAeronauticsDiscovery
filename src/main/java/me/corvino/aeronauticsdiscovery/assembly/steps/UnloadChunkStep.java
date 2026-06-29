package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.util.ChunkLoadingHelper;
import me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager;


public class UnloadChunkStep extends AssemblyStep {

    @Override
    protected AssemblyResult tick(AssemblyContext ctx) {
        if (ctx.template == null || ctx.level == null || ctx.anchor == null) return AssemblyResult.FAIL;

        ChunkLoadingHelper.ChunkBounds bounds = ChunkLoadingHelper.calculateChunkBounds(ctx);
        for (int cx = bounds.minX(); cx <= bounds.maxX(); cx++)
            for (int cz = bounds.minZ(); cz <= bounds.maxZ(); cz++)
                FlyoverManager.ticketController.forceChunk(ctx.level, ctx.anchor, cx, cz, false, true);

        CreateAeronauticsDiscovery.LOGGER.info("[UnloadChunkStep] Unloaded {} chunks for '{}'",
                (bounds.maxX() - bounds.minX() + 1) * (bounds.maxZ() - bounds.minZ() + 1),
                ctx.templateId);

        return AssemblyResult.SUCCESS;
    }
}
