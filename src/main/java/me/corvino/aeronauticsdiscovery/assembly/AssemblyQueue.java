package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.benchmark.PrefabBenchmark;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AssemblyQueue extends SavedData {
    private static final String DATA_NAME = CreateAeronauticsDiscovery.MODID + "_assembly_queue";

    private final List<Entry> entries = new ArrayList<>();

    public record Entry(
            ResourceLocation templateId,
            AssemblyPipeline pipeline,
            AssemblyContext context,
            int retryCount
    ) {
        public Entry withRetryCount(int newCount) {
            return new Entry(templateId, pipeline, context, newCount);
        }
    }

    public static SavedData.Factory<AssemblyQueue> factory() {
        return new SavedData.Factory<>(AssemblyQueue::new, AssemblyQueue::load);
    }

    public static AssemblyQueue get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            get(level).process(level);
        }
    }

    public void enqueue(AssemblyPipeline pipeline, AssemblyContext ctx) {
        entries.add(new Entry(ctx.templateId, pipeline, ctx, 0));
        setDirty();
    }

    private void process(ServerLevel level) {
        if (entries.isEmpty()) return;

        long startNanos = System.nanoTime();
        int beforeCount = entries.size();

        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            AssemblyContext ctx = entry.context;
            ctx.injectLevel(level);

            if (ctx.trigger == TriggerType.PROXIMITY) {
                if (!isNearPlayer(level, ctx)) {
                    continue;
                }
                if (!isLoaded(level, ctx)) {
                    continue;
                }
            }

            if (entry.retryCount >= ctx.maxRetries) {
                CreateAeronauticsDiscovery.LOGGER.warn("[QUEUE] Discarding '{}' (src={}) after {} failed attempts",
                        ctx.templateId, ctx.source, entry.retryCount);
                it.remove();
                setDirty();
                continue;
            }

            AssemblyResult result = entry.pipeline.execute(ctx);

            switch (result) {
                case SUCCESS -> {
                    CreateAeronauticsDiscovery.LOGGER.info("[QUEUE] SUCCESS: '{}' (src={})",
                            ctx.templateId, ctx.source);
                    it.remove();
                    setDirty();
                }
                case FAIL -> {
                    CreateAeronauticsDiscovery.LOGGER.warn("[QUEUE] FAIL: '{}' (src={}, attempt {}/{})",
                            ctx.templateId, ctx.source, entry.retryCount + 1, ctx.maxRetries);
                    entry = entry.withRetryCount(entry.retryCount + 1);
                    setDirty();
                }
                case DEFER -> {
                    CreateAeronauticsDiscovery.LOGGER.debug("[QUEUE] DEFER: '{}' (src={}, attempt {}/{})",
                            ctx.templateId, ctx.source, entry.retryCount + 1, ctx.maxRetries);
                    entry = entry.withRetryCount(entry.retryCount + 1);
                    setDirty();
                }
            }
        }

        if (PrefabBenchmark.isActive()) {
            PrefabBenchmark.recordTick(
                    System.nanoTime() - startNanos,
                    beforeCount,
                    entries.size()
            );
        }
    }

    private static boolean isNearPlayer(ServerLevel level, AssemblyContext ctx) {
        if (ctx.bounds == null) return false;
        int distance = Math.max(1, ctx.activationDistance);
        double maxDistSqr = (double) distance * distance;
        BlockPos center = ctx.assemblerPos != null ? ctx.assemblerPos
                : new BlockPos(
                        (ctx.bounds.minX() + ctx.bounds.maxX()) / 2,
                        (ctx.bounds.minY() + ctx.bounds.maxY()) / 2,
                        (ctx.bounds.minZ() + ctx.bounds.maxZ()) / 2);
        return level.players().stream()
                .anyMatch(player -> player.distanceToSqr(center.getCenter()) <= maxDistSqr);
    }

    private static boolean isLoaded(ServerLevel level, AssemblyContext ctx) {
        if (ctx.bounds == null) return false;
        int minChunkX = SectionPos.blockToSectionCoord(ctx.bounds.minX());
        int maxChunkX = SectionPos.blockToSectionCoord(ctx.bounds.maxX());
        int minChunkZ = SectionPos.blockToSectionCoord(ctx.bounds.minZ());
        int maxChunkZ = SectionPos.blockToSectionCoord(ctx.bounds.maxZ());

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    // Serialization 

    private static AssemblyQueue load(CompoundTag tag, HolderLookup.Provider provider) {
        AssemblyQueue data = new AssemblyQueue();
        ListTag list = tag.getList("Entries", 10);
        for (int i = 0; i < list.size(); i++) {
            loadEntry(list.getCompound(i)).ifPresent(data.entries::add);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            list.add(saveEntry(entry));
        }
        tag.put("Entries", list);
        return tag;
    }

    private static CompoundTag saveEntry(Entry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Template", entry.templateId.toString());
        tag.putString("Pipeline", entry.pipeline.name());
        tag.putInt("RetryCount", entry.retryCount);
        tag.putString("Source", entry.context.source.name());
        tag.putString("Trigger", entry.context.trigger.name());
        writeOptPos(tag, "Anchor", entry.context.anchor);
        writeOptPos(tag, "TemplatePos", entry.context.templatePos);
        if (entry.context.rotationTemplate != null) {
            tag.putString("Rotation", entry.context.rotationTemplate.name());
        }
        writeBounds(tag, entry.context.bounds);
        tag.putDouble("YawRadians", entry.context.yawRadians);
        tag.putInt("ActivationDistance", entry.context.activationDistance);
        tag.putInt("MaxRetries", entry.context.maxRetries);
        writeOptPos(tag, "AssemblerPos", entry.context.assemblerPos);
        return tag;
    }

    private static java.util.Optional<Entry> loadEntry(CompoundTag tag) {
        try {
            ResourceLocation templateId = ResourceLocation.parse(tag.getString("Template"));
            AssemblyPipeline pipeline = Pipelines.byName(tag.getString("Pipeline"));
            AssemblySource source = AssemblySource.valueOf(tag.getString("Source"));
            TriggerType trigger = TriggerType.valueOf(tag.getString("Trigger"));

            AssemblyContext ctx = new AssemblyContext(
                    null, templateId, source, trigger,
                    readOptPos(tag, "Anchor"),
                    readOptPos(tag, "TemplatePos"),
                    tag.contains("Rotation") ? Rotation.valueOf(tag.getString("Rotation")) : null,
                    readBounds(tag),
                    null,
                    tag.getDouble("YawRadians"),
                    tag.getInt("ActivationDistance"),
                    tag.getInt("MaxRetries")
            );
            ctx.assemblerPos = readOptPos(tag, "AssemblerPos");

            int retryCount = tag.getInt("RetryCount");

            return java.util.Optional.of(new Entry(templateId, pipeline, ctx, retryCount));
        } catch (Exception e) {
            CreateAeronauticsDiscovery.LOGGER.error("[QUEUE] Failed to deserialize entry: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private static void writeOptPos(CompoundTag tag, String key, BlockPos pos) {
        if (pos == null) return;
        CompoundTag posTag = new CompoundTag();
        posTag.putInt("X", pos.getX());
        posTag.putInt("Y", pos.getY());
        posTag.putInt("Z", pos.getZ());
        tag.put(key, posTag);
    }

    private static BlockPos readOptPos(CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        CompoundTag posTag = tag.getCompound(key);
        return new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
    }

    private static void writeBounds(CompoundTag tag, BoundingBox bounds) {
        if (bounds == null) return;
        tag.putInt("MinX", bounds.minX());
        tag.putInt("MinY", bounds.minY());
        tag.putInt("MinZ", bounds.minZ());
        tag.putInt("MaxX", bounds.maxX());
        tag.putInt("MaxY", bounds.maxY());
        tag.putInt("MaxZ", bounds.maxZ());
    }

    private static BoundingBox readBounds(CompoundTag tag) {
        if (!tag.contains("MinX")) return null;
        return new BoundingBox(
                tag.getInt("MinX"), tag.getInt("MinY"), tag.getInt("MinZ"),
                tag.getInt("MaxX"), tag.getInt("MaxY"), tag.getInt("MaxZ")
        );
    }
}
