package me.corvino.aeronauticsdiscovery.assembly.queue;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Predicates backing {@link TriggerType#PROXIMITY}. Kept separate from
 * {@code TriggerType} so the enum stays readable and these checks can be
 * unit-tested or reused independently.
 */
final class ActivationChecks {

    private ActivationChecks() {}

    static boolean isNearPlayer(ServerLevel level, AssemblyContext ctx) {
        if (ctx.bounds == null) return false;
        int distance = Math.max(1, ctx.activationDistance);
        double maxDistSqr = (double) distance * distance;
        BlockPos center = ctx.anchor != null ? ctx.anchor : centerOf(ctx.bounds);
        return level.players().stream()
                .anyMatch(player -> player.distanceToSqr(center.getCenter()) <= maxDistSqr);
    }

    static boolean isChunksLoaded(ServerLevel level, AssemblyContext ctx) { // todo check if duplicate of utility function
        if (ctx.bounds == null) return false;
        int minChunkX = SectionPos.blockToSectionCoord(ctx.bounds.minX());
        int maxChunkX = SectionPos.blockToSectionCoord(ctx.bounds.maxX());
        int minChunkZ = SectionPos.blockToSectionCoord(ctx.bounds.minZ());
        int maxChunkZ = SectionPos.blockToSectionCoord(ctx.bounds.maxZ());

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BlockPos centerOf(BoundingBox bounds) {
        return new BlockPos(
                (bounds.minX() + bounds.maxX()) / 2,
                (bounds.minY() + bounds.maxY()) / 2,
                (bounds.minZ() + bounds.maxZ()) / 2);
    }
}