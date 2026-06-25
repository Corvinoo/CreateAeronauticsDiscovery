package me.corvino.aeronauticsdiscovery.assembly;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.benchmark.PrefabBenchmark;
import me.corvino.aeronauticsdiscovery.event.FlyoverManager;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import me.corvino.aeronauticsdiscovery.physics.PrefabPhysicsConfig;
import me.corvino.aeronauticsdiscovery.physics.PrefabPhysicsRegistry;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class AssemblyQueue extends SavedData {
    private static final String DATA_NAME = CreateAeronauticsDiscovery.MODID + "_assembly_queue";

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> pendingAdd = new ArrayList<>();
    private boolean processing = false;
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

    public List<Entry> getEntries() {
        return List.copyOf(entries);
    }

    public void enqueue(AssemblyPipeline pipeline, AssemblyContext ctx) {
        Entry entry = new Entry(ctx.templateId, pipeline, ctx, 0);
        if (processing) {
            pendingAdd.add(entry);
        } else {
            entries.add(entry);
        }
        setDirty();
    }

    private void process(ServerLevel level) {
        if (entries.isEmpty() && pendingAdd.isEmpty()) return;

        long startNanos = System.nanoTime();
        int beforeCount = entries.size();

        processing = true;
        try {
            ListIterator<Entry> it = entries.listIterator();
            while (it.hasNext()) {
                Entry entry = it.next();
                AssemblyContext ctx = entry.context;
                ctx.injectLevel(level);

                if (ctx.trigger == TriggerType.PROXIMITY) {
                    if (!isNearPlayer(level, ctx)) continue;
                    if (!isLoaded(level, ctx))     continue;
                }

                if (entry.retryCount >= ctx.maxRetries) {
                    CreateAeronauticsDiscovery.LOGGER.warn(
                            "[QUEUE] Discarding '{}' (src={}) after {} failed attempts",
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
                        runPostAssembly(level, ctx);
                    }
                    case FAIL -> {
                        CreateAeronauticsDiscovery.LOGGER.warn(
                                "[QUEUE] FAIL: '{}' (src={}, attempt {}/{})",
                                ctx.templateId, ctx.source, entry.retryCount + 1, ctx.maxRetries);
                        it.set(entry.withRetryCount(entry.retryCount + 1));
                        setDirty();
                    }
                }
            }
        } finally {
            processing = false;
            if (!pendingAdd.isEmpty()) {
                entries.addAll(pendingAdd);
                pendingAdd.clear();
                setDirty();
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
    private static void runPostAssembly(ServerLevel level, AssemblyContext ctx) {
        if (ctx.assemblyResult == null
                || !(ctx.assemblyResult.subLevel() instanceof ServerSubLevel subLevel)) return;
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) return;

        PostAssembly.teleportBodyYaw(ctx, handle);
        PostAssembly.applyVelocity(ctx, handle);
        PostAssembly.nameSubLevel(ctx, subLevel);
        PostAssembly.registerFlyover(level, ctx, subLevel);
    }

    

    private static InitialVelocity resolveVelocity(AssemblyContext ctx) {
        if (ctx.velocityOverride != null && !ctx.velocityOverride.equals(InitialVelocity.NONE)) {
            return ctx.velocityOverride;
        }
        return PrefabPhysicsRegistry.getInstance().get(ctx.templateId)
                .map(PrefabPhysicsConfig::initialVelocity)
                .orElse(InitialVelocity.NONE);
    }

    private static Vec3 rotateVec3(Vec3 vec, double yawRadians) {
        if (yawRadians == 0.0) return vec;
        double cos = Math.cos(yawRadians);
        double sin = Math.sin(yawRadians);
        return new Vec3(
                vec.x * cos + vec.z * sin,
                vec.y,
                -vec.x * sin + vec.z * cos
        );
    }

    private static boolean isNearPlayer(ServerLevel level, AssemblyContext ctx) {
        if (ctx.bounds == null) return false;
        int distance = Math.max(1, ctx.activationDistance);
        double maxDistSqr = (double) distance * distance;
        BlockPos center = ctx.anchor != null ? ctx.anchor
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
        writeOptPos(tag, "AssemblerPos", entry.context.assemblerPos);
        writeOptPos(tag, "TemplatePos", entry.context.templatePos);
        if (entry.context.rotationTemplate != null) {
            tag.putString("Rotation", entry.context.rotationTemplate.name());
        }
        writeBounds(tag, entry.context.bounds);
        tag.putInt("ActivationDistance", entry.context.activationDistance);
        tag.putInt("MaxRetries", entry.context.maxRetries);
        tag.putDouble("YawRadians", entry.context.yawRadians);
        if (entry.context.subLevelName != null) {
            tag.putString("SubLevelName", entry.context.subLevelName);
        }
        tag.putBoolean("RegisterAsFlyover", entry.context.registerAsFlyover);
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
                    tag.getInt("ActivationDistance"),
                    tag.getInt("MaxRetries")
            );
            ctx.yawRadians = tag.getDouble("YawRadians");
            if (tag.contains("AssemblerPos")) {
                ctx.assemblerPos = readOptPos(tag, "AssemblerPos");
            }
            if (tag.contains("SubLevelName")) {
                ctx.subLevelName = tag.getString("SubLevelName");
            }
            ctx.registerAsFlyover = tag.getBoolean("RegisterAsFlyover");

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
    
    private static class PostAssembly{
        private static void teleportBodyYaw(AssemblyContext ctx, RigidBodyHandle handle) {
            if (ctx.yawRadians == 0.0 || ctx.bounds == null) return;
            Vector3d bodyPos = new Vector3d(
                    ctx.bounds.minX() + (ctx.bounds.maxX() - ctx.bounds.minX() + 1) / 2.0,
                    ctx.bounds.minY() + (ctx.bounds.maxY() - ctx.bounds.minY() + 1) / 2.0,
                    ctx.bounds.minZ() + (ctx.bounds.maxZ() - ctx.bounds.minZ() + 1) / 2.0
            );
            handle.teleport(bodyPos, new Quaterniond().rotationY(ctx.yawRadians));
        }

        private static void applyVelocity(AssemblyContext ctx, RigidBodyHandle handle) {
            InitialVelocity vel = resolveVelocity(ctx);
            if (vel == null || vel.equals(InitialVelocity.NONE)) return;

            Vec3 linear = vel.linear();
            Vec3 angular = vel.angular();
            if (ctx.yawRadians != 0.0) {
                linear = rotateVec3(linear, ctx.yawRadians);
                angular = rotateVec3(angular, ctx.yawRadians);
            }

            CreateAeronauticsDiscovery.LOGGER.info("[PHYSICS] Applying velocity to '{}': linear={}, angular={}, impulse={}",
                    ctx.templateId, linear, angular, vel.impulse());

            if (vel.impulse()) {
                handle.applyLinearAndAngularImpulse(
                        new org.joml.Vector3d(linear.x, linear.y, linear.z),
                        new org.joml.Vector3d(angular.x, angular.y, angular.z)
                );
            } else {
                handle.addLinearAndAngularVelocity(
                        new org.joml.Vector3d(linear.x, linear.y, linear.z),
                        new org.joml.Vector3d(angular.x, angular.y, angular.z)
                );
            }
        }

        private static void nameSubLevel(AssemblyContext ctx, ServerSubLevel subLevel) {
            String name = ctx.subLevelName != null ? ctx.subLevelName : ctx.templateId.getPath();
            subLevel.setName(name);
        }

        private static void registerFlyover(ServerLevel level, AssemblyContext ctx, ServerSubLevel subLevel) {
            if (ctx.registerAsFlyover) {
                FlyoverManager.get(level).addFlyover(subLevel, ctx.templateId);
            }
        }
    }
}
