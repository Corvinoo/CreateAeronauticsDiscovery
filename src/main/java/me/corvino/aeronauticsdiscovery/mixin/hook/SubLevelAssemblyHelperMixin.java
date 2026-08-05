package me.corvino.aeronauticsdiscovery.mixin.hook;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.util.LogCategory;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Moves {@link PinEntity} instances into the assembled sub-level. Sable's {@code moveOtherStuff} only relocates
 * hanging entities, so pins (plain entities) are moved here after Sable's own entity pass.
 */
@Mixin(SubLevelAssemblyHelper.class)
public abstract class SubLevelAssemblyHelperMixin {

    @Inject(
            method = "moveOtherStuff",
            at = @At("TAIL")
    )
    private static void aeronauticsdiscovery$movePins(
            ServerLevel level,
            SubLevelAssemblyHelper.AssemblyTransform transform,
            Iterable<BlockPos> blocks,
            BoundingBox3ic bounds,
            CallbackInfo ci
    ) {
        List<PinEntity> entities = level.getEntitiesOfClass(PinEntity.class, bounds.toAABB().inflate(2.0));
        if (entities.isEmpty()) return;

        BoundedBitVolume3i volume = BoundedBitVolume3i.fromBlocks(blocks);
        if (volume == null) return;

        int moved = 0;
        for (Entity entity : entities) {
            if (!(entity instanceof PinEntity pin)) continue;

            BlockPos pos = pin.blockPosition();
            if (volume.getOccupied(pos.getX(), pos.getY(), pos.getZ())) {
                pin.setPos(transform.apply(pin.position()));
                moved++;
            } else {
                ModLog.warn(LogCategory.GEN,
                        "Pin at {} NOT moved into sub-level: block position not inside assembled volume (bounds {})"
                                + " - pin will be left behind and appear as a missing pin",
                        pos, bounds);
            }
        }

        if (moved < entities.size()) {
            ModLog.warn(LogCategory.GEN, "Moved {}/{} pin(s) into sub-level (bounds {})", moved, entities.size(), bounds);
        }
    }
}
