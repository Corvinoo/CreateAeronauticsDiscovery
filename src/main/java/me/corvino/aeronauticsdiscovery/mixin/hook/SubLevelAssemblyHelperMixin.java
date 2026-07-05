package me.corvino.aeronauticsdiscovery.mixin.hook;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SubLevelAssemblyHelper.class)
public abstract class SubLevelAssemblyHelperMixin {

    @Inject(
            method = "moveOtherStuff",
            at = @At("TAIL")
    )
    private static void aeronauticsdiscovery$moveMarkers(
            ServerLevel level,
            SubLevelAssemblyHelper.AssemblyTransform transform,
            Iterable<BlockPos> blocks,
            BoundingBox3ic bounds,
            CallbackInfo ci
    ) {
        List<MarkerEntity> entities = level.getEntitiesOfClass(MarkerEntity.class, bounds.toAABB().inflate(2.0));
        if (entities.isEmpty()) return;

        BoundedBitVolume3i volume = BoundedBitVolume3i.fromBlocks(blocks);
        if (volume == null) return;

        for (Entity entity : entities) {
            if (!(entity instanceof MarkerEntity marker)) continue;

            BlockPos pos = marker.blockPosition();
            if (volume.getOccupied(pos.getX(), pos.getY(), pos.getZ())) {
                marker.setPos(transform.apply(marker.position()));
            }
        }
    }
}