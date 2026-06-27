package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.helper.ChunkLoadingHelper;
import me.corvino.aeronauticsdiscovery.assembly.scheduler.StepScheduler;
import me.corvino.aeronauticsdiscovery.event.FlyoverManager;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class LoadChunkStep implements DeferrableStep {
    private final StepScheduler scheduler = new StepScheduler(LoadChunkStep.class);

    @Override
    public AssemblyResult begin(AssemblyContext ctx) {
        if (ctx.template == null || ctx.level == null || ctx.anchor == null)
            return AssemblyResult.FAIL;

        ChunkLoadingHelper.ChunkBounds bounds = ChunkLoadingHelper.calculateChunkBounds(ctx);
        for (int cx = bounds.minX(); cx <= bounds.maxX(); cx++)
            for (int cz = bounds.minZ(); cz <= bounds.maxZ(); cz++)
                FlyoverManager.ticketController.forceChunk(
                        ctx.level, ctx.anchor, cx, cz, true, true);

        scheduler.scheduleAfter(ctx, 2);
        return AssemblyResult.WAITING;
    }

    @Override
    public AssemblyResult poll(AssemblyContext ctx) {
        if (ctx.level == null) return AssemblyResult.FAIL;
        if (!scheduler.isReady(ctx)) return AssemblyResult.WAITING;

        ChunkLoadingHelper.ChunkBounds bounds = ChunkLoadingHelper.calculateChunkBounds(ctx);
        int notReady = 0;
        for (int cx = bounds.minX(); cx <= bounds.maxX(); cx++) {
            for (int cz = bounds.minZ(); cz <= bounds.maxZ(); cz++) {
                long chunkKey = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
                if (!ctx.level.getChunkSource().isPositionTicking(chunkKey)) {
                    notReady++;
                }
            }
        }

        if (notReady > 0) {
            CreateAeronauticsDiscovery.LOGGER.debug(
                    "[LoadChunkStep] {}/{} chunks not ticking for '{}', waiting...",
                    notReady,
                    (bounds.maxX() - bounds.minX() + 1) * (bounds.maxZ() - bounds.minZ() + 1),
                    ctx.templateId);
            scheduler.scheduleAfter(ctx, 1);
            return AssemblyResult.WAITING;
        }

        scheduler.reset(ctx);
        CreateAeronauticsDiscovery.LOGGER.info(
                "[LoadChunkStep] All chunks ticking for '{}'", ctx.templateId);
        return AssemblyResult.SUCCESS;
    }

    @Override
    public void abort(AssemblyContext ctx) {
        scheduler.reset(ctx);
        releaseTickets(ctx);
    }

    @Override
    public void cleanup(AssemblyContext ctx) {
        DeferrableStep.super.cleanup(ctx);
    }

    private void releaseTickets(AssemblyContext ctx) {
        if (ctx.template == null || ctx.level == null || ctx.anchor == null) return;
        ChunkLoadingHelper.ChunkBounds bounds = ChunkLoadingHelper.calculateChunkBounds(ctx);
        for (int cx = bounds.minX(); cx <= bounds.maxX(); cx++)
            for (int cz = bounds.minZ(); cz <= bounds.maxZ(); cz++)
                FlyoverManager.ticketController.forceChunk(
                        ctx.level, ctx.anchor, cx, cz, false, true);
    }
}