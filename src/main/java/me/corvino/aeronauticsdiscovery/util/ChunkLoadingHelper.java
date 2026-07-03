package me.corvino.aeronauticsdiscovery.util;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;

public class ChunkLoadingHelper {

    public static ChunkBounds calculateChunkBounds(AssemblyContext ctx) {
        Vec3i size;
        size = ctx.structureTemplate().getSize();

        BlockPos anchor = ctx.anchor;
        assert anchor != null;

        int minBlockX = anchor.getX();
        int minBlockZ = anchor.getZ();
        int maxBlockX = anchor.getX() + size.getX();
        int maxBlockZ = anchor.getZ() + size.getZ();

        int minChunkX = SectionPos.blockToSectionCoord(minBlockX);
        int minChunkZ = SectionPos.blockToSectionCoord(minBlockZ);
        int maxChunkX = SectionPos.blockToSectionCoord(maxBlockX);
        int maxChunkZ = SectionPos.blockToSectionCoord(maxBlockZ);

        return new ChunkBounds(minChunkX, minChunkZ, maxChunkX, maxChunkZ);
    }

    public static ChunkBounds calculateChunkBounds(ServerSubLevel serverSubLevel) {
        var level = serverSubLevel.getLevel();
        var sublevelGlobalBounds = serverSubLevel.boundingBox();

        int minChunkX = SectionPos.blockToSectionCoord(sublevelGlobalBounds.minX());
        int minChunkZ = SectionPos.blockToSectionCoord(sublevelGlobalBounds.minZ());
        int maxChunkX = SectionPos.blockToSectionCoord(sublevelGlobalBounds.maxX());
        int maxChunkZ = SectionPos.blockToSectionCoord(sublevelGlobalBounds.maxZ());
        return new ChunkBounds(minChunkX, minChunkZ, maxChunkX,maxChunkZ);
    }

    public record ChunkBounds(int minX, int minZ, int maxX, int maxZ) {
    }
}

