package me.corvino.aeronauticsdiscovery.util;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.event.FlyoverSubLevelObserver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;

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

    public static ChunkBounds calculateChunkBounds(ServerSubLevel serverSubLevel) {
        var level = serverSubLevel.getLevel();
        int minX = 0, minZ = 0, maxX = 0, maxZ = 0;
        return new ChunkBounds(minX, minZ, maxX, maxZ);
    }

    public record ChunkBounds(int minX, int minZ, int maxX, int maxZ) {}
}

