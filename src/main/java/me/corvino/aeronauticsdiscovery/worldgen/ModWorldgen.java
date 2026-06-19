package me.corvino.aeronauticsdiscovery.worldgen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery.MODID;

public final class ModWorldgen {
    private ModWorldgen() {}

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(BuiltInRegistries.STRUCTURE_TYPE, MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES = DeferredRegister.create(BuiltInRegistries.STRUCTURE_PIECE, MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<GeneratedPrefabStructure>> GENERATED_PREFAB_STRUCTURE = STRUCTURE_TYPES.register(
            "generated_prefab",
            () -> () -> GeneratedPrefabStructure.CODEC
    );

    public static final DeferredHolder<StructurePieceType, StructurePieceType> GENERATED_PREFAB_PIECE = STRUCTURE_PIECE_TYPES.register(
            "generated_prefab",
            () -> (StructurePieceType.StructureTemplateType) GeneratedPrefabPiece::new
    );
}
