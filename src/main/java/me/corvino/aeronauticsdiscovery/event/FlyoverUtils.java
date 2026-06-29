package me.corvino.aeronauticsdiscovery.event;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager.FLYOVER_ID_TAG;
import static me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager.ticketController;

public class FlyoverUtils {
    public static void removeAllEntitiesInSublevel(ServerSubLevel subLevel, boolean forceLoadChunks) {
        removeAllEntitiesInSublevel(subLevel, forceLoadChunks, null, true);
    }

    public static void removeAllEntitiesInSublevel(
            ServerSubLevel subLevel,
            boolean forceLoadChunks,
            @Nullable Predicate<Entity> filter,
            boolean onlyOwnedBySubLevel) {

        ServerLevel level = subLevel.getLevel();
        UUID subLevelId   = subLevel.getUniqueId();
        AABB bb           = subLevel.boundingBox().toMojang();

        Predicate<Entity> effectiveFilter = buildEntityFilter(subLevelId, filter, onlyOwnedBySubLevel);

        int minCX = SectionPos.blockToSectionCoord((int) bb.minX);
        int minCZ = SectionPos.blockToSectionCoord((int) bb.minZ);
        int maxCX = SectionPos.blockToSectionCoord((int) bb.maxX);
        int maxCZ = SectionPos.blockToSectionCoord((int) bb.maxZ);

        if (forceLoadChunks) forEachChunk(level, subLevelId, minCX, minCZ, maxCX, maxCZ, true);
        removeMatchingEntities(level, bb, subLevelId, effectiveFilter);
        if (forceLoadChunks) forEachChunk(level, subLevelId, minCX, minCZ, maxCX, maxCZ, false);
    }

    private static Predicate<Entity> buildEntityFilter(
            UUID subLevelId,
            @Nullable Predicate<Entity> extraFilter,
            boolean onlyOwnedBySubLevel) {
        return entity -> {
            if (entity instanceof ServerPlayer) return false;
            if (extraFilter != null && !extraFilter.test(entity)) return false;
            if (!onlyOwnedBySubLevel) return true;
            CompoundTag data = entity.getPersistentData();
            return data.hasUUID(FLYOVER_ID_TAG) && data.getUUID(FLYOVER_ID_TAG).equals(subLevelId);
        };
    }

    private static void removeMatchingEntities(
            ServerLevel level,
            AABB bb,
            UUID subLevelId,
            Predicate<Entity> filter) {

        List<Entity> toRemove = new ArrayList<>();

        // Pass 1: entities Sable knows belong to this SubLevel
        level.getAllEntities().forEach(entity -> {
            if (entity == null || !filter.test(entity)) return;
            SubLevel containing = Sable.HELPER.getContaining(entity);
            if (containing == null || !containing.getUniqueId().equals(subLevelId)) return;
            collectWithPassengers(entity, filter, toRemove);
        });

        // Pass 2: spatial sweep for anything that slipped through Sable tracking
        level.getEntities((Entity) null, bb, filter)
                .forEach(entity -> collectWithPassengers(entity, filter, toRemove));

        toRemove.forEach(e -> e.remove(Entity.RemovalReason.DISCARDED));
    }

    private static void collectWithPassengers(Entity entity, Predicate<Entity> filter, List<Entity> out) {
        entity.getPassengers().forEach(passenger -> {
            if (filter.test(passenger)) {
                passenger.stopRiding();
                out.add(passenger);
            }
        });
        out.add(entity);
    }

    private static void forEachChunk(
            ServerLevel level, UUID id,
            int minCX, int minCZ, int maxCX, int maxCZ,
            boolean add) {
        for (int cx = minCX; cx <= maxCX; cx++)
            for (int cz = minCZ; cz <= maxCZ; cz++)
                ticketController.forceChunk(level, id, cx, cz, add, true);
    }
}
