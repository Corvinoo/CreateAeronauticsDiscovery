package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.util.ChunkLoadingHelper;
import me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager;


public class UnloadChunkStep extends AssemblyStep {
    @Override
    protected void build(Sequence seq) {
        seq.require(ctx -> ctx.template != null || ctx.templateSize, "Template or size missing")
                .require(ctx -> ctx.level != null, "Level missing")
                .require(ctx -> ctx.anchor != null, "Anchor missing")
                .run(this::unloadChunks);
    }

    private void unloadChunks(AssemblyContext ctx) {
        assert ctx.anchor != null;
        assert ctx.level != null;

        ChunkLoadingHelper.ChunkBounds bounds = ChunkLoadingHelper.calculateChunkBounds(ctx);
        for (int cx = bounds.minX(); cx <= bounds.maxX(); cx++)
            for (int cz = bounds.minZ(); cz <= bounds.maxZ(); cz++) {
                FlyoverManager.ticketController.forceChunk(ctx.level, ctx.anchor, cx, cz, false, true);
            }

        CreateAeronauticsDiscovery.LOGGER.info("[UnloadChunkStep] Unloaded {} chunks for '{}'",
                (bounds.maxX() - bounds.minX() + 1) * (bounds.maxZ() - bounds.minZ() + 1),
                ctx.templateId);
    }
}
