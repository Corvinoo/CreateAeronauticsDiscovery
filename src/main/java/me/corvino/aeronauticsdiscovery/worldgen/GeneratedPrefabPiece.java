package me.corvino.aeronauticsdiscovery.worldgen;

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

public class GeneratedPrefabPiece extends TemplateStructurePiece {

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

        this.enqueueAssemblies(level, chunkPos);
    }

    @Override
    protected void handleDataMarker(String metadata, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox bounds) {
    }

    /**
     * The pipeline's FindAssemblyStartStep discovers the assembly origin, after every chunk of the template bounds is ticking
     */
    private void enqueueAssemblies(WorldGenLevel level, ChunkPos chunkPos) {
        if (!chunkPos.equals(new ChunkPos(this.templatePosition))) {
            return;
        }
        ResourceLocation templateId = ResourceLocation.parse(this.templateName);
        BoundingBox templateBounds = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
        var serverLevel = level.getLevel();

        // anchor is the template origin
        AssemblyQueue.get(serverLevel).enqueue(Pipelines.WORLDGEN,
                AssemblyContext.builder()
                        .level(serverLevel)
                        .anchor(this.templatePosition)
                        .templateId(templateId)
                        .source(AssemblySource.WORLDGEN)
                        .rotationTemplate(this.placeSettings.getRotation())
                        .build());

        ModLog.info(QUEUE, "Enqueued WORLDGEN discovery for '{}' (origin {}, bounds {})",
                templateId, this.templatePosition, templateBounds);
    }
}
