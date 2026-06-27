package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.event.FlyoverManager;
import me.corvino.aeronauticsdiscovery.assembly.helper.ChunkLoadingHelper;

public class UnloadChunkStep implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        if(ctx.template == null) return AssemblyResult.FAIL;
        if(ctx.level == null) return AssemblyResult.FAIL;
        assert ctx.anchor != null;

        ChunkLoadingHelper.ChunkBounds bounds = ChunkLoadingHelper.calculateChunkBounds(ctx);

        for (int cx = bounds.minX(); cx <= bounds.maxX(); cx++) {
            for (int cz = bounds.minZ(); cz <= bounds.maxZ(); cz++) {
                FlyoverManager.ticketController.forceChunk(
                        ctx.level,
                        ctx.anchor,
                        cx,
                        cz,
                        false,
                        true
                );
            }
        }

        CreateAeronauticsDiscovery.LOGGER.info("Unloaded {} chunks for flyover at {}",
                (bounds.maxX() - bounds.minX() + 1) * (bounds.maxZ() - bounds.minZ() + 1),
                ctx.anchor
        );

        return AssemblyResult.SUCCESS;
    }
}
