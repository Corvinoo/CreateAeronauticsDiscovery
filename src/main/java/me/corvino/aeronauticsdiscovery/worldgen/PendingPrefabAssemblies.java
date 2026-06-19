package me.corvino.aeronauticsdiscovery.worldgen;

import dev.simulated_team.simulated.util.SimAssemblyHelper;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.PrefabService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class PendingPrefabAssemblies extends SavedData {
    public static final int DEFAULT_ACTIVATION_DISTANCE = 128;
    private static final String DATA_NAME = CreateAeronauticsDiscovery.MODID + "_pending_prefab_assemblies";
    private static final int MAX_ASSEMBLIES_PER_TICK = 1;
    private static final int MAX_RETRIES = 60;
    private static final ResourceLocation HONEY_GLUE_ID = ResourceLocation.parse("simulated:honey_glue");

    private final List<Entry> entries = new ArrayList<>();

    public static SavedData.Factory<PendingPrefabAssemblies> factory() {
        return new SavedData.Factory<>(PendingPrefabAssemblies::new, PendingPrefabAssemblies::load);
    }

    public static PendingPrefabAssemblies get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static void enqueue(
            ServerLevel level,
            ResourceLocation template,
            BlockPos templatePos,
            BlockPos assemblerPos,
            Rotation rotation,
            BoundingBox bounds,
            int activationDistance
    ) {
        get(level).add(new Entry(template, templatePos, assemblerPos, rotation, bounds, activationDistance, 0));
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            get(level).process(level);
        }
    }

    private static PendingPrefabAssemblies load(CompoundTag tag, HolderLookup.Provider provider) {
        PendingPrefabAssemblies data = new PendingPrefabAssemblies();
        ListTag list = tag.getList("Entries", 10);
        for (int i = 0; i < list.size(); i++) {
            data.entries.add(Entry.load(list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Entry entry : this.entries) {
            list.add(entry.save());
        }
        tag.put("Entries", list);
        return tag;
    }

    private void add(Entry entry) {
        for (Entry existing : this.entries) {
            if (existing.assemblerPos.equals(entry.assemblerPos)) {
                return;
            }
        }

        this.entries.add(entry);
        this.setDirty();
    }

    private void process(ServerLevel level) {
        if (this.entries.isEmpty()) {
            return;
        }

        int processed = 0;
        ListIterator<Entry> listIterator = this.entries.listIterator();
        while (listIterator.hasNext() && processed < MAX_ASSEMBLIES_PER_TICK) {
            Entry entry = listIterator.next();

            if (!entry.isNearPlayer(level)) {
                continue;
            }

            if (!entry.isLoaded(level)) {
                CreateAeronauticsDiscovery.LOGGER.info("[PROCESS] Entry {} chunks not fully loaded yet", entry.assemblerPos());
                continue;
            }

            // Discard if exceeded max retries
            if (entry.retryCount >= MAX_RETRIES) {
                CreateAeronauticsDiscovery.LOGGER.warn("[PROCESS] Discarding entry at {} after {} failed attempts",
                        entry.assemblerPos(), entry.retryCount);
                listIterator.remove();
                this.setDirty();
                processed++;
                continue;
            }

            // Wait for honey glue entity to load before assembling 
            if (!hasHoneyGlueEntity(level, entry.bounds)) {
                CreateAeronauticsDiscovery.LOGGER.info("[PROCESS] Entry at {}: honey glue entity not loaded (attempt {}/{})",
                        entry.assemblerPos(), entry.retryCount + 1, MAX_RETRIES);
                listIterator.set(entry.withRetryCount(entry.retryCount + 1));
                this.setDirty();
                processed++;
                continue;
            }

            CreateAeronauticsDiscovery.LOGGER.info("[PROCESS] Attempting assembly at {}...", entry.assemblerPos());

            try {
                SimAssemblyHelper.AssemblyResult result = PrefabService.assembleFromPlacedBlock(level, entry.assemblerPos());
                PrefabService.applyInitialVelocity(level, result, entry.template());
                listIterator.remove();
                this.setDirty();
                processed++;
                CreateAeronauticsDiscovery.LOGGER.info("[PROCESS] Assembly SUCCESS at {} (sublevel={}, template={})",
                        entry.assemblerPos(), result.subLevel(), entry.template());
            } catch (Exception exception) {
                CreateAeronauticsDiscovery.LOGGER.warn("[PROCESS] Assembly FAILED at {} (attempt {}/{}): {}",
                        entry.assemblerPos(), entry.retryCount + 1, MAX_RETRIES, exception.getMessage());
                // Retry next tick — transient entity-loading delays should not be permanent
                listIterator.set(entry.withRetryCount(entry.retryCount + 1));
                this.setDirty();
                processed++;
            }
        }
    }

    private static boolean hasHoneyGlueEntity(ServerLevel level, BoundingBox bounds) {
        EntityType<?> glueType = BuiltInRegistries.ENTITY_TYPE.get(HONEY_GLUE_ID);
        if (glueType == null) return false;

        AABB aabb = new AABB(
                bounds.minX() - 1, bounds.minY() - 1, bounds.minZ() - 1,
                bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1
        );
        return !level.getEntities(glueType, aabb, e -> true).isEmpty();
    }

    private record Entry(
            ResourceLocation template,
            BlockPos templatePos,
            BlockPos assemblerPos,
            Rotation rotation,
            BoundingBox bounds,
            int activationDistance,
            int retryCount
    ) {
        private Entry withRetryCount(int newCount) {
            return new Entry(template, templatePos, assemblerPos, rotation, bounds, activationDistance, newCount);
        }
        private boolean isNearPlayer(ServerLevel level) {
            int distance = Math.max(1, this.activationDistance);
            double maxDistanceSqr = (double) distance * distance;
            return level.players().stream().anyMatch(player -> player.distanceToSqr(this.assemblerPos.getCenter()) <= maxDistanceSqr);
        }

        private boolean isLoaded(ServerLevel level) {
            int minChunkX = SectionPos.blockToSectionCoord(this.bounds.minX());
            int maxChunkX = SectionPos.blockToSectionCoord(this.bounds.maxX());
            int minChunkZ = SectionPos.blockToSectionCoord(this.bounds.minZ());
            int maxChunkZ = SectionPos.blockToSectionCoord(this.bounds.maxZ());

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                        return false;
                    }
                }
            }

            return true;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Template", this.template.toString());
            writePos(tag, "TemplatePos", this.templatePos);
            writePos(tag, "AssemblerPos", this.assemblerPos);
            tag.putString("Rotation", this.rotation.name());
            tag.putInt("MinX", this.bounds.minX());
            tag.putInt("MinY", this.bounds.minY());
            tag.putInt("MinZ", this.bounds.minZ());
            tag.putInt("MaxX", this.bounds.maxX());
            tag.putInt("MaxY", this.bounds.maxY());
            tag.putInt("MaxZ", this.bounds.maxZ());
            tag.putInt("ActivationDistance", this.activationDistance);
            tag.putInt("RetryCount", this.retryCount);
            return tag;
        }

        private static Entry load(CompoundTag tag) {
            return new Entry(
                    ResourceLocation.parse(tag.getString("Template")),
                    readPos(tag, "TemplatePos"),
                    readPos(tag, "AssemblerPos"),
                    Rotation.valueOf(tag.getString("Rotation")),
                    new BoundingBox(
                            tag.getInt("MinX"),
                            tag.getInt("MinY"),
                            tag.getInt("MinZ"),
                            tag.getInt("MaxX"),
                            tag.getInt("MaxY"),
                            tag.getInt("MaxZ")
                    ),
                    tag.getInt("ActivationDistance"),
                    tag.getInt("RetryCount")
            );
        }

        private static void writePos(CompoundTag tag, String key, BlockPos pos) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", pos.getX());
            posTag.putInt("Y", pos.getY());
            posTag.putInt("Z", pos.getZ());
            tag.put(key, posTag);
        }

        private static BlockPos readPos(CompoundTag tag, String key) {
            CompoundTag posTag = tag.getCompound(key);
            return new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
        }
    }
}
