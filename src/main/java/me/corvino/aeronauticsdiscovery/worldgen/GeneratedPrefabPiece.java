package me.corvino.aeronauticsdiscovery.worldgen;

import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyQueue;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import me.corvino.aeronauticsdiscovery.assembly.Pipelines;
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

public class GeneratedPrefabPiece extends TemplateStructurePiece {
    private final int activationDistance;

    public GeneratedPrefabPiece(
            StructureTemplateManager templateManager,
            ResourceLocation template,
            BlockPos pos,
            Rotation rotation,
            int activationDistance
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
        this.activationDistance = activationDistance;
    }

    public GeneratedPrefabPiece(StructureTemplateManager templateManager, CompoundTag tag) {
        super(ModWorldgen.GENERATED_PREFAB_PIECE.get(), tag, templateManager, location -> makeSettings(Rotation.valueOf(tag.getString("Rot"))));
        this.activationDistance = tag.getInt("ActivationDistance");
    }

    private static StructurePlaceSettings makeSettings(Rotation rotation) {
        return new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(rotation)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", this.placeSettings.getRotation().name());
        tag.putInt("ActivationDistance", this.activationDistance);
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

        CreateAeronauticsDiscovery.LOGGER.info("[GEN] Placed Prefab Template '{}' at {} in chunk {}. Rotation: {}",
            this.templateName, this.templatePosition, chunkPos, this.placeSettings.getRotation());

        this.enqueueAssemblies(level);
    }

    @Override
    protected void handleDataMarker(String metadata, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox bounds) {
    }

    private void enqueueAssemblies(WorldGenLevel level) {
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
                assemblerCount++;
                queue.enqueue(Pipelines.WORLDGEN,
                        AssemblyContext.builder(serverLevel, templateId, AssemblySource.WORLDGEN)
                                .templatePos(this.templatePosition)
                                .rotationTemplate(this.placeSettings.getRotation())
                                .bounds(templateBounds)
                                .activationDistance(this.activationDistance)
                                .assemblerPos(worldPos)
                                .build());

                CreateAeronauticsDiscovery.LOGGER.info("[QUEUE] Queued assembly for PhysicsAssembler at {} (Template: {}, Dist: {})",
                        worldPos, templateId, this.activationDistance);
            }
        }

        if (assemblerCount == 0 && firstNonAir != null) { 
            CreateAeronauticsDiscovery.LOGGER.debug("[QUEUE] No PhysicsAssemblerBlock in template '{}'; using fallback anchor at {}",
                    templateId, firstNonAir);
            queue.enqueue(Pipelines.WORLDGEN,
                    AssemblyContext.builder(serverLevel, templateId, AssemblySource.WORLDGEN)
                            .templatePos(this.templatePosition)
                            .rotationTemplate(this.placeSettings.getRotation())
                            .bounds(templateBounds)
                            .activationDistance(this.activationDistance)
                            .assemblerPos(firstNonAir)
                            .build());
        } else if (assemblerCount == 0) {
            CreateAeronauticsDiscovery.LOGGER.warn("[WARN] Template '{}' placed with NO blocks at all!", templateId);
        }
    }
}
