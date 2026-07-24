package me.corvino.aeronauticsdiscovery.event;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.corvino.aeronauticsdiscovery.mixin.accessor.LevelAccessor;
import me.corvino.aeronauticsdiscovery.mixin.accessor.PersistentEntitySectionManagerAccessor;
import me.corvino.aeronauticsdiscovery.scheduler.TaskScheduler;
import me.corvino.aeronauticsdiscovery.util.ChunkLoadingHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;
import static me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager.ticketController;

public class FlyoverUtils {

    public static void removeAllEntitiesInSublevel(ServerSubLevel subLevel, boolean forceLoadChunks) {
        removeAllEntitiesInSublevel(subLevel, forceLoadChunks, null, true);
    }

    public static void removeAllEntitiesInSublevel(
            @NotNull ServerSubLevel subLevel,
            boolean forceLoadChunks,
            @Nullable Predicate<Entity> filter,
            boolean onlyOwnedBySubLevel) {

        ServerLevel level = subLevel.getLevel();
        UUID subLevelId = subLevel.getUniqueId();
        var bounds = ChunkLoadingHelper.calculateChunkBounds(subLevel);

        Predicate<Entity> effectiveFilter = buildEntityFilter(subLevelId, filter, onlyOwnedBySubLevel);
        LongSet chunkKeys = getSubLevelChunkKeys(subLevel);

        if (forceLoadChunks) {
            forEachChunk(level, subLevelId, bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), true);
        }

        removeEntitiesInChunks(level, chunkKeys, effectiveFilter);

        if (forceLoadChunks) {
            forEachChunk(level, subLevelId, bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), false);
        }
    }

    private static Predicate<Entity> buildEntityFilter(
            UUID subLevelId,
            @Nullable Predicate<Entity> extraFilter,
            boolean onlyOwnedBySubLevel) {

        return entity -> {
            if (entity instanceof Player) return false;
            if (extraFilter != null && !extraFilter.test(entity)) return false;
            if (!onlyOwnedBySubLevel) return true;

            CompoundTag data = entity.getPersistentData();
            return data.hasUUID(SUBLEVEL_ID_TAG) && data.getUUID(SUBLEVEL_ID_TAG).equals(subLevelId);
        };
    }

    private static LongSet getSubLevelChunkKeys(ServerSubLevel subLevel) {
        ServerLevelPlot plot = subLevel.getPlot();
        LongSet chunkKeys = new LongOpenHashSet();

        for (PlotChunkHolder holder : plot.getLoadedChunks()) {
            chunkKeys.add(holder.getPos().toLong());
        }

        return chunkKeys;
    }

    private static void removeEntitiesInChunks(ServerLevel level, LongSet chunkKeys, Predicate<Entity> filter) {
        PersistentEntitySectionManager<Entity> manager =
                ((LevelAccessor) level).getEntityManager();

        @SuppressWarnings("unchecked")
        EntitySectionStorage<Entity> sectionStorage =
                ((PersistentEntitySectionManagerAccessor<Entity>) manager).getSectionStorage();

        for (long chunkKey : chunkKeys) {
            Stream<EntitySection<Entity>> sections = sectionStorage.getExistingSectionsInChunk(chunkKey);
            for (EntitySection<Entity> section : sections.toList()) {
                for (Entity entity : section.getEntities().toList()) {
                    if (filter.test(entity)) {
                        entity.stopRiding();
                        entity.ejectPassengers();

                        entity.remove(Entity.RemovalReason.DISCARDED);
                        section.remove(entity);
                    }
                }
            }
        }
    }

    private static void forEachChunk(
            ServerLevel level, UUID id,
            int minCX, int minCZ, int maxCX, int maxCZ,
            boolean add) {
        for (int cx = minCX; cx <= maxCX; cx++)
            for (int cz = minCZ; cz <= maxCZ; cz++)
                ticketController.forceChunk(level, id, cx, cz, add, true);
    }

    public static boolean isPlayerNearSubLevel(SubLevel subLevel, double inflation) {
        AABB proximityBox = subLevel.boundingBox().toMojang().inflate(inflation);
        for (ServerPlayer player : subLevel.getLevel().getServer().getPlayerList().getPlayers()) {
            if (proximityBox.contains(player.position().x, player.position().y, player.position().z)) {
                return true;
            }
        }
        return false;
    }

    public static CompletableFuture<Void> removeAllEntitiesInSublevelAwaitingChunks(ServerSubLevel subLevel) {
        ServerLevel level = subLevel.getLevel();
        UUID subLevelId = subLevel.getUniqueId();
        var bounds = ChunkLoadingHelper.calculateChunkBounds(subLevel);

        forEachChunk(level, subLevelId, bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), true);

        CompletableFuture<Void> result = TaskScheduler.getInstance().runSyncRepeatingUntil(future -> {
            boolean allReady = true;
            for (int cx = bounds.minX(); cx <= bounds.maxX() && allReady; cx++) {
                for (int cz = bounds.minZ(); cz <= bounds.maxZ(); cz++) {
                    if (!level.getChunkSource().isPositionTicking(ChunkPos.asLong(cx, cz))) {
                        allReady = false;
                        break;
                    }
                }
            }

            if (allReady) {
                removeAllEntitiesInSublevel(subLevel, false);
                future.complete(null);
            }
        }, 20, 100_000);

        return result.whenComplete((v, ex) ->
                forEachChunk(level, subLevelId, bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), false));
    }
}