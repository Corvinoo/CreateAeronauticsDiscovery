package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyStep;
import me.corvino.aeronauticsdiscovery.event.FlyoverManager;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;

public class UnloadChunkStep implements AssemblyStep{

    @Override
    public AssemblyResult run(AssemblyContext ctx) {

        if(ctx.template == null) return AssemblyResult.FAIL;
        if(ctx.level == null) return AssemblyResult.FAIL;
        Vec3i size = ctx.template.getSize();
        int radiusBlocks = Math.max(size.getX(), size.getZ()) / 2 + 16;
        var spawnPos = ctx.anchor;
        int minCX = SectionPos.blockToSectionCoord(spawnPos.getX() - radiusBlocks);
        int minCZ = SectionPos.blockToSectionCoord(spawnPos.getZ() - radiusBlocks);
        int maxCX = SectionPos.blockToSectionCoord(spawnPos.getX() + radiusBlocks);
        int maxCZ = SectionPos.blockToSectionCoord(spawnPos.getZ() + radiusBlocks);

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                FlyoverManager.ticketController.forceChunk(ctx.level, spawnPos, cx, cz, false, true);
            }
        }
        
        return AssemblyResult.SUCCESS;
    }
}
