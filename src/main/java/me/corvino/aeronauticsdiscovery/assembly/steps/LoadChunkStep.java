package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager;
import me.corvino.aeronauticsdiscovery.util.ChunkLoadingHelper;
import net.minecraft.world.level.ChunkPos;

public class LoadChunkStep extends AssemblyStep {
    private ChunkLoadingHelper.ChunkBounds bounds;
    private boolean ticketsForced = false;

    @Override
    protected int timeoutTicks() {
        return 600;
    }

    @Override
    protected void build(Sequence seq) {
        seq.require(ctx -> ctx.template != null, "Template missing")
                .require(ctx -> ctx.level != null, "Level missing")
                .require(ctx -> ctx.anchor != null, "Anchor missing")
                .run(this::computeBoundsAndForceTickets)
                .waitUntil(this::allChunksTicking)
                .run(ctx -> CreateAeronauticsDiscovery.LOGGER.info(
                        "[LoadChunkStep] All chunks are ticking for '{}'", ctx.templateId));
    }

    private void computeBoundsAndForceTickets(AssemblyContext ctx) {
        bounds = ChunkLoadingHelper.calculateChunkBounds(ctx);
        forceTickets(ctx, bounds, true);
        ticketsForced = true;
    }

    private boolean allChunksTicking(AssemblyContext ctx) {
        assert ctx.level != null;
        int notReady = 0;
        for (int cx = bounds.minX(); cx <= bounds.maxX(); cx++)
            for (int cz = bounds.minZ(); cz <= bounds.maxZ(); cz++) {
                if (!ctx.level.getChunkSource().isPositionTicking(ChunkPos.asLong(cx, cz)))
                    notReady++;
            }
        if (notReady > 0) {
            CreateAeronauticsDiscovery.LOGGER.debug(
                    "[LoadChunkStep] {} chunk(s) not ticking for '{}', waiting..",
                    notReady, ctx.templateId);
            return false;
        }
        return true;
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        if (ticketsForced) releaseTickets(ctx);
    }

    private void forceTickets(AssemblyContext ctx, ChunkLoadingHelper.ChunkBounds b, boolean add) {
        assert ctx.level != null;
        assert ctx.anchor != null;
        for (int cx = b.minX(); cx <= b.maxX(); cx++)
            for (int cz = b.minZ(); cz <= b.maxZ(); cz++) {
                FlyoverManager.ticketController.forceChunk(ctx.level, ctx.anchor, cx, cz, add, true);
            }
    }

    private void releaseTickets(AssemblyContext ctx) {
        if (ctx.template == null || ctx.level == null || ctx.anchor == null) return;
        forceTickets(ctx, bounds, false);
    }
}