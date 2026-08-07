package me.corvino.aeronauticsdiscovery.patrol;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import me.corvino.aeronauticsdiscovery.assembly.Pipelines;
import me.corvino.aeronauticsdiscovery.assembly.PrefabService;
import me.corvino.aeronauticsdiscovery.assembly.queue.AssemblyQueue;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotPlan;
import me.corvino.aeronauticsdiscovery.autopilot.SpawnPlacement;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.PATROL;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class PatrolSpawner {

    private PatrolSpawner() {}

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        PatrolRegistry registry = PatrolRegistry.getInstance();
        if (registry == null || registry.getConfigs().isEmpty()) return;

        var starts = event.getChunk().getAllStarts();
        if (starts.isEmpty()) return;

        ChunkPos loadedChunk = event.getChunk().getPos();

        for (PatrolConfig config : registry.getConfigs()) {
            if (!config.allowedIn(level.dimension().location())) continue;
            for (StructureStart start : starts.values()) {
                if (!matches(level, start.getStructure(), config.targetStructure())) continue;
                // Only act when this chunk is the structure's OWN start chunk.
                if (!start.getChunkPos().equals(loadedChunk)) continue;
                handleStructureStart(level, config, start);
            }
        }
    }

    private static void handleStructureStart(ServerLevel level, PatrolConfig config, StructureStart start) {
        long packedChunk = start.getChunkPos().toLong();
        PatrolManager manager = PatrolManager.get(level);
        if (manager.isHandled(config.targetStructure(), packedChunk)) {
            ModLog.debug(PATROL, "{} @ chunk {} already handled, skipping", config.targetStructure(), packedChunk);
            return;
        }
        manager.markHandled(config.targetStructure(), packedChunk);

        if (level.random.nextDouble() >= config.chance()) {
            ModLog.info(PATROL, "Chance ({}) rejected patrol '{}' for {} @ chunk {}",
                    config.chance(), config.template(), config.targetStructure(), packedChunk);
            return;
        }

        try {
            spawnPatrol(level, config, start);
        } catch (Exception e) {
            ModLog.error(PATROL, "Failed to spawn patrol '{}' for {} @ chunk {}: {}",
                    config.template(), config.targetStructure(), packedChunk, e.getMessage());
        }
    }

    private static void spawnPatrol(ServerLevel level, PatrolConfig config, StructureStart start) {
        BlockPos center = resolveCenter(level, start);
        if (center == null) {
            ModLog.warn(PATROL, "Could not resolve center for {} @ {}; skipping patrol",
                    config.targetStructure(), start.getChunkPos());
            return;
        }

        int altitude = config.minAltitude() + (config.maxAltitude() > config.minAltitude()
                ? level.random.nextInt(config.maxAltitude() - config.minAltitude() + 1)
                : 0);

        // Placement (offset + heading) is derived from the flight plan itself, e.g. an orbit goal
        // starts the craft on its ring facing the tangent. No offset/yaw config needed.
        SpawnPlacement placement = config.plan()
                .map(AutopilotPlan::spawnPlacement)
                .orElse(SpawnPlacement.NONE);

        // Anchor the TEMPLATE ORIGIN so the template CENTER lands at (center + placement), altitude.
        Vec3i size = templateSize(level, config.template());
        BlockPos anchor = new BlockPos(
                center.getX() + placement.offsetX() - (int) Math.floor(size.getX() / 2.0),
                altitude - size.getY() / 2,
                center.getZ() + placement.offsetZ() - (int) Math.floor(size.getZ() / 2.0));

        double yaw = placement.yawRadians();

        AssemblyContext ctx = AssemblyContext.builder()
                .level(level)
                .anchor(anchor)
                .templateId(config.template())
                .source(AssemblySource.PATROL)
                .rotationTemplate(Rotation.NONE)
                .setYaw(yaw)
                .overrideVelocity(config.initialVelocity().orElse(null))
                .overridePlan(config.plan().orElse(null))
                .maxRetries(3)
                .setName("patrol-" + config.targetStructure().replace(':', '_'))
                .build();

        AssemblyQueue.get(level).enqueue(Pipelines.PATROL, ctx);

        ModLog.info(PATROL,
                "Enqueued patrol '{}' for {} @ chunk {}: anchor={} yaw={}deg placement=({}, {})",
                config.template(), config.targetStructure(), start.getChunkPos(),
                anchor, Math.toDegrees(yaw), placement.offsetX(), placement.offsetZ());
    }

    /**
     * Resolves the structure's located position (what {@code /locate} reports and what the structure
     * is centered on) via its random-spread placement, falling back to the start chunk's min block.
     */
    private static BlockPos resolveCenter(ServerLevel level, StructureStart start) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> key = registry.getResourceKey(start.getStructure()).orElse(null);
        if (key == null) return null;
        Holder<Structure> holder = registry.getHolder(key).orElse(null);
        if (holder == null) return null;

        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
            if (placement instanceof RandomSpreadStructurePlacement randomSpread) {
                return randomSpread.getLocatePos(start.getChunkPos());
            }
        }
        return new BlockPos(start.getChunkPos().getMinBlockX(), 0, start.getChunkPos().getMinBlockZ());
    }

    private static boolean matches(ServerLevel level, Structure structure, String ref) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> key = registry.getResourceKey(structure).orElse(null);
        if (key == null) return false;

        if (ref.startsWith("#")) {
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(ref.substring(1)));
            return registry.getTag(tag)
                    .map(holders -> holders.stream().anyMatch(h -> h.is(key)))
                    .orElse(false);
        }
        return registry.getKey(structure).equals(ResourceLocation.parse(ref));
    }

    private static Vec3i templateSize(ServerLevel level, ResourceLocation templateId) {
        return PrefabService.loadPrefab(level, templateId).getSize();
    }
}
