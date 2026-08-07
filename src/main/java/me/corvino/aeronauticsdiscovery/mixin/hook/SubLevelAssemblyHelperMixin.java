package me.corvino.aeronauticsdiscovery.mixin.hook;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import me.corvino.aeronauticsdiscovery.mixin.accessor.LevelAccessor;
import me.corvino.aeronauticsdiscovery.mixin.accessor.PersistentEntitySectionManagerAccessor;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.util.LogCategory;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;

/**
 * Moves {@link PinEntity} instances into the assembled sub-level. Sable's {@code moveOtherStuff} only relocates
 * hanging entities, so pins (plain entities) are moved here.
 */
@Mixin(SubLevelAssemblyHelper.class)
public abstract class SubLevelAssemblyHelperMixin {

    @Inject(
            method = "moveOtherStuff",
            at = @At("HEAD")
    )
    private static void aeronauticsdiscovery$movePins(
            ServerLevel level,
            SubLevelAssemblyHelper.AssemblyTransform transform,
            Iterable<BlockPos> blocks,
            BoundingBox3ic bounds,
            CallbackInfo ci
    ) {
        AABB box = bounds.toAABB().inflate(2.0);
        List<PinEntity> pins = findPinsBySectionScan(level, box);
        if (pins.isEmpty()) return;

        int filteredVisible = level.getEntitiesOfClass(PinEntity.class, box).size();

        int moved = 0;
        for (PinEntity pin : pins) {
            // Never steal pins already bound to a sub-level (e.g. from child assemblies).
            if (pin.getPersistentData().hasUUID(SUBLEVEL_ID_TAG)) continue;

            BlockPos pos = pin.blockPosition();
            if (!bounds.contains(pos.getX(), pos.getY(), pos.getZ())) continue;

            pin.setPos(transform.apply(pin.position()));
            moved++;
        }

        if (moved > 0 || filteredVisible != pins.size()) {
            ModLog.info(LogCategory.GEN,
                    "movePins: moved {}/{} pin(s) into sub-level (bounds {}; found via section-scan: {}, visible to filtered query: {})",
                    moved, pins.size(), bounds, pins.size(), filteredVisible);
        }
    }

    /**
     * Iterates every existing entity section intersecting {@code box} directly (accessible or not),
     * bypassing the accessibility filter of {@code ServerLevel#getEntitiesOfClass}
     */
    private static List<PinEntity> findPinsBySectionScan(ServerLevel level, AABB box) {
        PersistentEntitySectionManager<Entity> manager = ((LevelAccessor) level).getEntityManager();
        EntitySectionStorage<Entity> storage = ((PersistentEntitySectionManagerAccessor<Entity>) manager).getSectionStorage();

        List<PinEntity> found = new ArrayList<>();
        int minSX = SectionPos.blockToSectionCoord(box.minX);
        int maxSX = SectionPos.blockToSectionCoord(box.maxX);
        int minSY = SectionPos.blockToSectionCoord(box.minY);
        int maxSY = SectionPos.blockToSectionCoord(box.maxY);
        int minSZ = SectionPos.blockToSectionCoord(box.minZ);
        int maxSZ = SectionPos.blockToSectionCoord(box.maxZ);

        for (int sx = minSX; sx <= maxSX; sx++) {
            for (int sy = minSY; sy <= maxSY; sy++) {
                for (int sz = minSZ; sz <= maxSZ; sz++) {
                    EntitySection<Entity> section = storage.getSection(SectionPos.asLong(sx, sy, sz));
                    if (section == null) continue;
                    for (Entity entity : section.getEntities().toList()) {
                        if (entity instanceof PinEntity pin && pin.getBoundingBox().intersects(box)) {
                            found.add(pin);
                        }
                    }
                }
            }
        }
        return found;
    }
}
