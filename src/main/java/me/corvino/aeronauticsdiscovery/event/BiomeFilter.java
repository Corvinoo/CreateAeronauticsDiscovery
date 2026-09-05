package me.corvino.aeronauticsdiscovery.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

public record BiomeFilter(boolean deny, List<ResourceLocation> biomes) {

    public static final BiomeFilter ALL = new BiomeFilter(false, List.of());

    public static final Codec<BiomeFilter> CODEC = Codec
            .unboundedMap(Codec.STRING, ResourceLocation.CODEC.listOf())
            .flatXmap(
                    map -> {
                        if (map.isEmpty()) return DataResult.success(ALL);
                        if (map.size() > 1) {
                            return DataResult.error(() ->
                                    "biome_filter must use exactly one of 'only' or 'exclude'");
                        }
                        Map.Entry<String, List<ResourceLocation>> entry =
                                map.entrySet().iterator().next();
                        return switch (entry.getKey()) {
                            case "only" -> DataResult.success(new BiomeFilter(false, entry.getValue()));
                            case "exclude" -> DataResult.success(new BiomeFilter(true, entry.getValue()));
                            default -> DataResult.error(() ->
                                    "unknown biome_filter mode '" + entry.getKey() + "', expected 'only' or 'exclude'");
                        };
                    },
                    filter -> filter.biomes.isEmpty()
                            ? DataResult.success(Map.of())
                            : DataResult.success(Map.of(filter.deny ? "exclude" : "only", filter.biomes))
            );

    public boolean matches(ServerLevel level, BlockPos pos) {
        if (biomes.isEmpty()) return true;
        ResourceLocation biome = level.getBiome(pos)
                .unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);
        if (biome == null) return false;
        return deny != biomes.contains(biome);
    }
}
