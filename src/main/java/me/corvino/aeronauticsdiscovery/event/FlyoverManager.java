package me.corvino.aeronauticsdiscovery.event;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlyoverManager extends SavedData {
    private static final String DATA_NAME = CreateAeronauticsDiscovery.MODID + "_flyovers";
    private static final String TAG_KEY = "Flyovers";

    private final Map<UUID, FlyoverData> flyovers = new LinkedHashMap<>();
    private ServerLevel level;

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
    public SubLevel getSubLevel(UUID subLevelId) {
        if (this.level == null) return null;
        SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        return container.getSubLevel(subLevelId);
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

    private static void discardBoundEntities(ServerLevel level, SubLevel subLevel) {
        AABB bb = subLevel.boundingBox().toMojang();
        for (Entity entity : level.getEntities((Entity) null, bb, e -> !(e instanceof ServerPlayer))) {
            entity.discard();
        }
    }

    public void tick() {
        if (this.flyovers.isEmpty()) return;

        int maxLifetime = Config.flyoverMaxLifetimeTicks;
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, FlyoverData> entry : this.flyovers.entrySet()) {
            FlyoverData data = entry.getValue();
            SubLevel subLevel = this.getSubLevel(data.subLevelId());

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
                CreateAeronauticsDiscovery.LOGGER.info("[FLYOVER] Despawning flyover {} (template '{}') after {} ticks",
                        data.subLevelId(), data.templateId(), data.lifeTicks());
                discardBoundEntities(this.level, subLevel);
                subLevel.markRemoved();
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
