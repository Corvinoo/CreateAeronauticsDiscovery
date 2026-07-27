package me.corvino.aeronauticsdiscovery.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.util.StructureSearchWorker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StructureMapLootFunction implements LootItemFunction {

    public static final MapCodec<StructureMapLootFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.listOf().optionalFieldOf("structures", List.<String>of()).forGetter(f -> f.structureIds),
                    Codec.STRING.optionalFieldOf("decoration").forGetter(f -> Optional.ofNullable(f.decoration)),
                    Codec.STRING.optionalFieldOf("name").forGetter(f -> Optional.ofNullable(f.name))
            ).apply(instance, (structures, decoration, name) ->
                    new StructureMapLootFunction(structures, decoration.orElse(null), name.orElse(null)))
    );

    private final List<String> structureIds;
    private final String decoration;
    private final String name;

    public StructureMapLootFunction(List<String> structureIds, String decoration, String name) {
        this.structureIds = structureIds;
        this.decoration = decoration;
        this.name = name;
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        ServerLevel level = context.getLevel();
        Vec3 originVec = context.getParamOrNull(LootContextParams.ORIGIN);
        BlockPos origin = originVec != null ? BlockPos.containing(originVec) : BlockPos.ZERO;

        List<? extends String> ids = structureIds.isEmpty()
                ? Config.traderStructureMaps
                : structureIds;

        if (ids.isEmpty()) return stack;

        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        long seed = state.getLevelSeed();

        List<String> shuffled = new ArrayList<>(ids);
        RandomSource random = level.getRandom();
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            String tmp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, tmp);
        }

        for (String id : shuffled) {
            ItemStack map = tryStructure(level, registry, state, seed, id, origin);
            if (map != null) return map;
        }

        return stack;
    }

    private ItemStack tryStructure(ServerLevel level, Registry<Structure> registry,
                                  ChunkGeneratorStructureState state, long seed, String structId, BlockPos origin) {
        ResourceLocation loc = ResourceLocation.parse(structId);
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, loc);
        var holderOpt = registry.getHolder(key);
        if (holderOpt.isEmpty()) return null;

        for (StructurePlacement sp : state.getPlacementsForStructure(holderOpt.get())) {
            if (sp instanceof RandomSpreadStructurePlacement rssp) {
                BlockPos found = StructureSearchWorker.searchNearest(
                        level, holderOpt.get().value(), rssp, seed, origin, 50, 800);
                if (found != null) {
                    Holder<MapDecorationType> deco = resolveDecoration(level);
                    ItemStack map = MapItem.create(level, found.getX(), found.getZ(), (byte) 2, true, true);
                    MapItemSavedData.addTargetDecoration(map, found, "+", deco);
                    applyName(map, loc);
                    return map;
                }
            }
        }
        return null;
    }

    private Holder<MapDecorationType> resolveDecoration(ServerLevel level) {
        if (decoration != null) {
            ResourceLocation decoLoc = ResourceLocation.parse(decoration);
            ResourceKey<MapDecorationType> decoKey = ResourceKey.create(Registries.MAP_DECORATION_TYPE, decoLoc);
            return level.registryAccess().registryOrThrow(Registries.MAP_DECORATION_TYPE)
                    .getHolder(decoKey)
                    .<Holder<MapDecorationType>>map(h -> h)
                    .orElse(MapDecorationTypes.TARGET_X);
        }
        return MapDecorationTypes.TARGET_X;
    }

    private void applyName(ItemStack map, ResourceLocation structureLoc) {
        if (name != null) {
            map.set(DataComponents.ITEM_NAME, Component.literal(name));
        } else {
            String[] parts = structureLoc.getPath().split("_");
            StringBuilder nameBuilder = new StringBuilder();
            for (String p : parts) {
                nameBuilder.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
            }
            nameBuilder.append("Map");
            map.set(DataComponents.ITEM_NAME, Component.literal(nameBuilder.toString()));
        }
    }

    @Override
    public LootItemFunctionType<?> getType() {
        return ModLootFunctions.STRUCTURE_MAP.get();
    }
}
