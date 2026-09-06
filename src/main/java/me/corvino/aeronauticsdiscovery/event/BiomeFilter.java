package me.corvino.aeronauticsdiscovery.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

public record BiomeFilter(boolean deny, List<String> patterns) {

    public static final BiomeFilter ALL = new BiomeFilter(false, List.of());

    public static final Codec<BiomeFilter> CODEC = Codec
            .unboundedMap(Codec.STRING, Codec.STRING.listOf())
            .flatXmap(
                    map -> {
                        if (map.isEmpty()) return DataResult.success(ALL);
                        if (map.size() > 1) {
                            return DataResult.error(() ->
                                    "biome_filter must use exactly one of 'only' or 'exclude'");
                        }
                        Map.Entry<String, List<String>> entry =
                                map.entrySet().iterator().next();
                        return switch (entry.getKey()) {
                            case "only" -> DataResult.success(new BiomeFilter(false, List.copyOf(entry.getValue())));
                            case "exclude" -> DataResult.success(new BiomeFilter(true, List.copyOf(entry.getValue())));
                            default -> DataResult.error(() ->
                                    "unknown biome_filter mode '" + entry.getKey() + "', expected 'only' or 'exclude'");
                        };
                    },
                    filter -> filter.patterns.isEmpty()
                            ? DataResult.success(Map.of())
                            : DataResult.success(Map.of(filter.deny ? "exclude" : "only", filter.patterns))
            );

    public boolean matches(ServerLevel level, BlockPos pos) {
        if (patterns.isEmpty()) return true;
        ResourceLocation biome = level.getBiome(pos)
                .unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);
        if (biome == null) return false;
        return matchesBiome(biome);
    }

    public boolean matchesBiome(ResourceLocation biome) {
        boolean anyMatch = false;
        for (String pattern : patterns) {
            if (wildcardMatch(pattern, biome)) {
                anyMatch = true;
                break;
            }
        }
        return deny != anyMatch;
    }

    static boolean wildcardMatch(String pattern, ResourceLocation biome) {
        String target = pattern.contains(":") ? biome.toString() : biome.getPath();
        return globMatches(pattern, target);
    }

    static boolean globMatches(String pattern, String text) {
        int p = 0, t = 0, star = -1, match = 0;
        while (t < text.length()) {
            if (p < pattern.length() && pattern.charAt(p) == text.charAt(t)) {
                p++;
                t++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                match = t;
            } else if (star != -1) {
                p = star + 1;
                t = ++match;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') p++;
        return p == pattern.length();
    }
}
