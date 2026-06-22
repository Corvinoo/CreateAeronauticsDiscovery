package me.corvino.aeronauticsdiscovery.event;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class FlyoverManager extends SavedData {
    public static TicketController ticketController = new TicketController(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsDiscovery.MODID, "chunkticketmanager")
    );

    private static final String DATA_NAME = CreateAeronauticsDiscovery.MODID + "_flyovers";
    private static final String TAG_KEY = "Flyovers";

    final Map<UUID, FlyoverData> flyovers = new LinkedHashMap<>();
    private ServerLevel level;

    private boolean observerRegistered = false;

    public FlyoverManager() {
        this(null);
    }

    public FlyoverManager(ServerLevel level) {
        this.level = level;
    }

    public static FlyoverManager get(ServerLevel level) {
        FlyoverManager manager = level.getDataStorage().computeIfAbsent(
                new Factory<>(FlyoverManager::new, (tag, provider) -> load(level, tag), null),
                DATA_NAME);
        manager.level = level;

        if (!manager.observerRegistered) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                container.addObserver(new FlyoverSubLevelObserver(manager));
                manager.observerRegistered = true;
            }
        }

        return manager;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            get(level).tick();
        }
    }

    @javax.annotation.Nullable
    public FlyoverData getFlyoverData(UUID subLevelId) {
        return this.flyovers.get(subLevelId);
    }

    @javax.annotation.Nullable
    public ServerSubLevel getSubLevel(UUID subLevelId) {
        if (this.level == null) return null;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null) return null;
        var sublevelFound = container.getSubLevel(subLevelId);
        if (!(sublevelFound instanceof ServerSubLevel)) return null;
        return (ServerSubLevel) sublevelFound;
    }

    public Map<UUID, FlyoverData> getAllFlyovers() {
        return java.util.Collections.unmodifiableMap(this.flyovers);
    }

    public void addFlyover(SubLevel subLevel, ResourceLocation templateId) {
        this.flyovers.put(subLevel.getUniqueId(), new FlyoverData(subLevel.getUniqueId(), 0, templateId));
        this.setDirty();
        CreateAeronauticsDiscovery.LOGGER.info("[FLYOVER] Registered '{}' (id={}) for despawn tracking (max {} ticks)",
                templateId, subLevel.getUniqueId(), Config.flyoverMaxLifetimeTicks);
    }

    private static boolean isPlayerNearSubLevel(ServerLevel level, SubLevel subLevel) {
        AABB check = subLevel.boundingBox().toMojang().inflate(5.0);
        for (ServerPlayer player : level.players()) {
            if (check.contains(player.position().x, player.position().y, player.position().z)) {
                return true;
            }
        }
        return false;
    }


    public void tick() {
        if (this.flyovers.isEmpty()) return;

        int maxLifetime = Config.flyoverMaxLifetimeTicks;
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, FlyoverData> entry : this.flyovers.entrySet()) {
            FlyoverData data = entry.getValue();
            var subLevel = this.getSubLevel(data.subLevelId());

            if (subLevel == null) {
                if (data.lifeTicks() >= maxLifetime) {
                    // Already expired in unloaded chunks; wait until chunks load
                    continue;
                }
                data = data.tick();
                entry.setValue(data);
                if (data.lifeTicks() >= maxLifetime) {
                    CreateAeronauticsDiscovery.LOGGER.info("[FLYOVER] Flyover {} (template '{}') expired at {} ticks (unloaded, pending removal when chunks load)",
                            data.subLevelId(), data.templateId(), data.lifeTicks());
                }
                continue;
            }

            if (subLevel.isRemoved()) {
                toRemove.add(entry.getKey());
                continue;
            }

            if (isPlayerNearSubLevel(this.level, subLevel)) {
                CreateAeronauticsDiscovery.LOGGER.info("[FLYOVER] Releasing flyover {} (template '{}'); player nearby",
                        data.subLevelId(), data.templateId());
                toRemove.add(entry.getKey());
                continue;
            }

            data = data.tick();
            entry.setValue(data);

            if (data.lifeTicks() >= maxLifetime) {
                removeAllEntitiesInSublevel(subLevel, true);
                Objects.requireNonNull(SubLevelContainer.getContainer(level))
                        .removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
                toRemove.add(entry.getKey());
            }
        }

        if (!toRemove.isEmpty()) {
            for (UUID id : toRemove) {
                this.flyovers.remove(id);
            }
            this.setDirty();
        }
    }



    public static void removeAllEntitiesInSublevel(ServerSubLevel subLevel, Boolean forceLoadChunks) {
        removeAllEntitiesInSublevel(subLevel, forceLoadChunks, null);
    }

    public static void removeAllEntitiesInSublevel(ServerSubLevel subLevel, Boolean forceLoadChunks, @Nullable Predicate<Entity> filter) {
        var level = (ServerLevel) subLevel.getLevel();
        var entitiesToRemove = new ArrayList<Entity>();

        level.getAllEntities().forEach(entity -> {
            if (entity instanceof ServerPlayer) return;
            if (entity == null) return;
            if (filter != null && !filter.test(entity)) return;
            SubLevel containing = Sable.HELPER.getContaining(entity);
            if (containing == null) return;
            if (!containing.getUniqueId().equals(subLevel.getUniqueId())) return;

            entity.getPassengers().forEach(passenger -> {
                if (!(passenger instanceof ServerPlayer) && (filter == null || filter.test(passenger))) {
                    passenger.stopRiding();
                    entitiesToRemove.add(passenger);
                }
            });
            entitiesToRemove.add(entity);
        });

        entitiesToRemove.forEach(e -> e.remove(Entity.RemovalReason.DISCARDED));
        entitiesToRemove.clear();

        AABB bb = subLevel.boundingBox().toMojang();
        int minCX = SectionPos.blockToSectionCoord((int) bb.minX);
        int minCZ = SectionPos.blockToSectionCoord((int) bb.minZ);
        int maxCX = SectionPos.blockToSectionCoord((int) bb.maxX);
        int maxCZ = SectionPos.blockToSectionCoord((int) bb.maxZ);

        if (forceLoadChunks) {
            for (int cx = minCX; cx <= maxCX; cx++)
                for (int cz = minCZ; cz <= maxCZ; cz++)
                    FlyoverManager.ticketController.forceChunk(level, subLevel.getUniqueId(), cx, cz, true, true);
        }

        level.getEntities((Entity) null, bb, e -> !(e instanceof ServerPlayer) && (filter == null || filter.test(e)))
                .forEach(entity -> {
                    entity.getPassengers().forEach(passenger -> {
                        if (!(passenger instanceof ServerPlayer) && (filter == null || filter.test(passenger))) {
                            passenger.stopRiding();
                            passenger.remove(Entity.RemovalReason.DISCARDED);
                        }
                    });
                    entity.remove(Entity.RemovalReason.DISCARDED);
                });

        if (forceLoadChunks) {
            for (int cx = minCX; cx <= maxCX; cx++)
                for (int cz = minCZ; cz <= maxCZ; cz++)
                    FlyoverManager.ticketController.forceChunk(level, subLevel.getUniqueId(), cx, cz, false, true);
        }
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        List<FlyoverData> values = List.copyOf(this.flyovers.values());
        tag.put(TAG_KEY, FlyoverData.CODEC.listOf().encodeStart(NbtOps.INSTANCE, values).getOrThrow());
        return tag;
    }

    private static FlyoverManager load(ServerLevel level, CompoundTag tag) {
        FlyoverManager manager = new FlyoverManager(level);
        if (tag.contains(TAG_KEY, 9)) {
            ListTag list = tag.getList(TAG_KEY, 10);
            FlyoverData.CODEC.listOf().parse(NbtOps.INSTANCE, list)
                    .resultOrPartial(error -> CreateAeronauticsDiscovery.LOGGER.warn("[FLYOVER] Failed to parse flyover data: {}", error))
                    .ifPresent(dataList -> {
                        for (FlyoverData data : dataList) {
                            manager.flyovers.put(data.subLevelId(), data);
                        }
                    });
        }
        return manager;
    }
}

