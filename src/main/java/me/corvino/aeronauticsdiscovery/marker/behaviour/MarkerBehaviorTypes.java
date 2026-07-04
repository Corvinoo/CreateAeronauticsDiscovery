package me.corvino.aeronauticsdiscovery.marker.behaviour;

import com.mojang.serialization.Codec;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MarkerBehaviorTypes {
    private static final Map<ResourceLocation, MarkerBehaviorType<?>> REGISTRY = new HashMap<>();

    private MarkerBehaviorTypes() {}

    public static <T extends MarkerBehavior<T>> MarkerBehaviorType<T> register(String path, Codec<T> codec) {
        return register(path, codec, List.of());
    }

    public static <T extends MarkerBehavior<T>> MarkerBehaviorType<T> register(String path, Codec<T> codec, List<ConfigField> configFields) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CreateAeronauticsDiscovery.MODID, path);
        MarkerBehaviorType<T> type = new MarkerBehaviorType<>(id, codec, configFields);
        if (REGISTRY.put(id, type) != null) {
            throw new IllegalStateException("Duplicate marker behavior registration: " + id);
        }
        return type;
    }

    @Nullable
    public static MarkerBehaviorType<?> byId(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Map<ResourceLocation, MarkerBehaviorType<?>> getAll() {
        return Map.copyOf(REGISTRY);
    }

    public static void bootstrap() {
        touch(ChainExplosiveBehavior.TYPE);
        touch(MobSpawnPointBehavior.TYPE);
    }

    private static void touch(MarkerBehaviorType<?> type) {
        // no-op; used referencing the type is used to trigger deferred registration
    }
}
