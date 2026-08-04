package me.corvino.aeronauticsdiscovery.worldgen;

import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import me.corvino.aeronauticsdiscovery.assembly.Pipelines;
import me.corvino.aeronauticsdiscovery.assembly.queue.AssemblyQueue;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.GEN;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.QUEUE;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.HashSet;
import java.util.Set;

public class GeneratedPrefabPiece extends TemplateStructurePiece {

    /**
     * postProcess runs once per chunk the piece overlaps; guard so the assembler(s)
     * are only queued once instead of once per overlapping chunk.
     */
    private final Set<BlockPos> enqueuedAssemblers = new HashSet<>();

    public GeneratedPrefabPiece(
            StructureTemplateManager templateManager,
            ResourceLocation template,
            BlockPos pos,
            Rotation rotation
    ) {
        super(
                ModWorldgen.GENERATED_PREFAB_PIECE.get(),
                0,
                templateManager,
                template,
                template.toString(),
                makeSettings(rotation),
                pos
        );
    }

    public GeneratedPrefabPiece(StructureTemplateManager templateManager, CompoundTag tag) {
        super(ModWorldgen.GENERATED_PREFAB_PIECE.get(), tag, templateManager, location -> makeSettings(Rotation.valueOf(tag.getString("Rot"))));
    }

    private static StructurePlaceSettings makeSettings(Rotation rotation) {
        return new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(rotation)
                .setIgnoreEntities(true)
                .setKnownShape(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", this.placeSettings.getRotation().name());
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            RandomSource random,
            BoundingBox bounds,
            ChunkPos chunkPos,
            BlockPos pivot
    ) {
        super.postProcess(level, structureManager, chunkGenerator, random, bounds, chunkPos, pivot);

        ModLog.info(GEN, "Placed Prefab Template '{}' at {} in chunk {}. Rotation: {}",
            this.templateName, this.templatePosition, chunkPos, this.placeSettings.getRotation());

        this.enqueueAssemblies(level, bounds);
    }

    @Override
    protected void handleDataMarker(String metadata, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox bounds) {
    }

    /**
     * {@code postProcess} is called once per chunk the piece overlaps. The world is only
     * partially generated for that chunk, so {@code level.getBlockState} cannot see blocks
     * in ungenerated neighbor chunks. We therefore only fall back to a non-assembler anchor
     * when the whole template is contained in the bounds currently being placed (single-chunk
     * templates, e.g. balloons); for multi-chunk templates the real PhysicsAssemblerBlock is
     * enqueued as soon as its own chunk is placed.
     */
    private void enqueueAssemblies(WorldGenLevel level, BoundingBox bounds) {
        ResourceLocation templateId = ResourceLocation.parse(this.templateName);
        BoundingBox templateBounds = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
        var serverLevel = level.getLevel();

        int assemblerCount = 0;
        BlockPos firstNonAir = null;
        AssemblyQueue queue = AssemblyQueue.get(serverLevel);

        for (BlockPos pos : BlockPos.betweenClosed(
                templateBounds.minX(), templateBounds.minY(), templateBounds.minZ(),
                templateBounds.maxX(), templateBounds.maxY(), templateBounds.maxZ()
        )) {
            BlockPos worldPos = pos.immutable();
            var state = level.getBlockState(worldPos);

            if (state.isAir()) continue;

            if (firstNonAir == null) {
                firstNonAir = worldPos;
            }

            if (state.getBlock() instanceof PhysicsAssemblerBlock) {
                if (!enqueuedAssemblers.add(worldPos)) continue;
                assemblerCount++;
                queue.enqueue(Pipelines.WORLDGEN,
                        AssemblyContext.builder()
                                .level(serverLevel)
                                .anchor(this.templatePosition)
                                .templateId(templateId)
                                .source(AssemblySource.WORLDGEN)
                                .rotationTemplate(this.placeSettings.getRotation())
                                .assemblerPos(worldPos)
                                .build());

                ModLog.info(QUEUE, "Queued assembly for PhysicsAssembler at {} (Template: {})",
                        worldPos, templateId);
            }
        }

        boolean templateFullyPlaced = fullyContains(bounds, templateBounds);

        if (assemblerCount == 0 && templateFullyPlaced) {
            if (firstNonAir != null && enqueuedAssemblers.add(firstNonAir)) {
                ModLog.debug(QUEUE, "No PhysicsAssemblerBlock in template '{}'; using fallback anchor at {}",
                        templateId, firstNonAir);
                queue.enqueue(Pipelines.WORLDGEN,
                        AssemblyContext.builder()
                                .level(serverLevel)
                                .anchor(firstNonAir)
                                .templateId(templateId)
                                .source(AssemblySource.WORLDGEN)
                                .rotationTemplate(this.placeSettings.getRotation())
                                .assemblerPos(firstNonAir)
                                .build());
            } else {
                ModLog.warn(GEN, "Template '{}' placed with NO blocks at all!", templateId);
            }
        }
    }

    private static boolean fullyContains(BoundingBox outer, BoundingBox inner) {
        return inner.minX() >= outer.minX() && inner.maxX() <= outer.maxX()
                && inner.minY() >= outer.minY() && inner.maxY() <= outer.maxY()
                && inner.minZ() >= outer.minZ() && inner.maxZ() <= outer.maxZ();
    }
}
