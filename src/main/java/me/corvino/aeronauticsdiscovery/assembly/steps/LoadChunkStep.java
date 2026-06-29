package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.helper.ChunkLoadingHelper;
import me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager;
import net.minecraft.world.level.ChunkPos;

public class LoadChunkStep extends AssemblyStep {
    private boolean ticketsForced = false;

    @Override
    protected int timeoutTicks() { return 600; }

    @Override
    protected AssemblyResult tick(AssemblyContext ctx) {
        if (ctx.template == null || ctx.level == null || ctx.anchor == null)
            return AssemblyResult.FAIL;

        ChunkLoadingHelper.ChunkBounds bounds = ChunkLoadingHelper.calculateChunkBounds(ctx);

        if (!ticketsForced) {
            forceTickets(ctx, bounds, true);
            ticketsForced = true;
            return AssemblyResult.WAITING;
        }

        int notReady = 0;
        for (int cx = bounds.minX(); cx <= bounds.maxX(); cx++)
            for (int cz = bounds.minZ(); cz <= bounds.maxZ(); cz++)
                if (!ctx.level.getChunkSource().isPositionTicking(ChunkPos.asLong(cx, cz)))
                    notReady++;

        if (notReady > 0) {
            CreateAeronauticsDiscovery.LOGGER.debug(
                    "[LoadChunkStep] {}/{} chunk(s) are not ticking for '{}', waiting..",
                    notReady,
                    (bounds.maxX() - bounds.minX() + 1) * (bounds.maxZ() - bounds.minZ() + 1),
                    ctx.templateId);
            return AssemblyResult.WAITING;
        }

        CreateAeronauticsDiscovery.LOGGER.info("[LoadChunkStep] All chunks are ticking for '{}'", ctx.templateId);
        return AssemblyResult.SUCCESS;
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        if (ticketsForced) releaseTickets(ctx);
    }

    private void forceTickets(AssemblyContext ctx, ChunkLoadingHelper.ChunkBounds bounds, boolean add) {
        assert ctx.anchor != null;
        assert ctx.level != null;
        for (int cx = bounds.minX(); cx <= bounds.maxX(); cx++)
            for (int cz = bounds.minZ(); cz <= bounds.maxZ(); cz++) {
                FlyoverManager.ticketController.forceChunk(ctx.level, ctx.anchor, cx, cz, add, true);
            }
    }

    private void releaseTickets(AssemblyContext ctx) {
        if (ctx.template == null || ctx.level == null || ctx.anchor == null) return;
        ChunkLoadingHelper.ChunkBounds bounds = ChunkLoadingHelper.calculateChunkBounds(ctx);
        forceTickets(ctx, bounds, false);
    }
}