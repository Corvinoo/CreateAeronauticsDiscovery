package me.corvino.aeronauticsdiscovery.util;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class ChunkLoadingHelper {

    public static ChunkBounds calculateChunkBounds(AssemblyContext ctx) {
        // Use the ACTUAL placed bounds (rotation-aware), not anchor + unrotated template size.
        // For rotated templates those can span different chunks (e.g. CCW90 swaps x/z), which
        // previously left some plane chunks un-force-loaded and made pins in them invisible to
        // the assembly-time entity query
        BoundingBox tb = ctx.templateBounds();
        return new ChunkBounds(
                SectionPos.blockToSectionCoord(tb.minX()),
                SectionPos.blockToSectionCoord(tb.minZ()),
                SectionPos.blockToSectionCoord(tb.maxX()),
                SectionPos.blockToSectionCoord(tb.maxZ()));
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

