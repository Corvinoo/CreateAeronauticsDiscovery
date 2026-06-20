package me.corvino.aeronauticsdiscovery.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

public class GeneratedPrefabStructure extends Structure {
    public static final MapCodec<GeneratedPrefabStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance),
            ResourceLocation.CODEC.fieldOf("template").forGetter(structure -> structure.template),
            Codec.INT.optionalFieldOf("fixed_y", 150).forGetter(structure -> structure.fixedY),
            Codec.BOOL.optionalFieldOf("random_rotation", true).forGetter(structure -> structure.randomRotation),
            Codec.INT.optionalFieldOf("activation_distance", 128).forGetter(structure -> structure.activationDistance)
    ).apply(instance, GeneratedPrefabStructure::new));

    private final ResourceLocation template;
    private final int fixedY;
    private final boolean randomRotation;
    private final int activationDistance;

    public GeneratedPrefabStructure(
            StructureSettings settings,
            ResourceLocation template,
            int fixedY,
            boolean randomRotation,
            int activationDistance
    ) {
        super(settings);
        this.template = template;
        this.fixedY = fixedY;
        this.randomRotation = randomRotation;
        this.activationDistance = activationDistance;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        Rotation rotation = this.randomRotation ? Rotation.getRandom(context.random()) : Rotation.NONE;
        BlockPos pos = new BlockPos(chunkPos.getMiddleBlockX(), this.fixedY, chunkPos.getMiddleBlockZ());

        return Optional.of(new GenerationStub(pos, pieces -> pieces.addPiece(new GeneratedPrefabPiece(
                context.structureTemplateManager(),
                this.template,
                pos,
                rotation,
                this.activationDistance
        ))));
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.GENERATED_PREFAB_STRUCTURE.get();
    }
}
