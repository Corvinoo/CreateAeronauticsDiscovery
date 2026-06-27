package me.corvino.aeronauticsdiscovery.assembly.helper;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkLoadingHelper {

    public static ChunkBounds calculateChunkBounds(AssemblyContext ctx) {
        assert ctx.template != null;
        Vec3i size = ctx.template.getSize();
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

    public record ChunkBounds(int minX, int minZ, int maxX, int maxZ) {}
}

