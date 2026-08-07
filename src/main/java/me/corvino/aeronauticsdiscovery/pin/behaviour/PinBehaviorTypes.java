package me.corvino.aeronauticsdiscovery.pin.behaviour;

import com.mojang.serialization.Codec;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PinBehaviorTypes {
    private static final Map<ResourceLocation, PinBehaviorType<?>> REGISTRY = new HashMap<>();

    private PinBehaviorTypes() {}

    public static <T extends PinBehavior<T>> PinBehaviorType<T> register(String path, Codec<T> codec, int color) {
        return register(path, codec, List.of(), color, false);
    }

    public static <T extends PinBehavior<T>> PinBehaviorType<T> register(String path, Codec<T> codec, List<ConfigField> configFields, int color) {
        return register(path, codec, configFields, color, false);
    }

    public static <T extends PinBehavior<T>> PinBehaviorType<T> register(String path, Codec<T> codec, List<ConfigField> configFields, int color, boolean deprecated) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CreateAeronauticsDiscovery.MODID, path);
        PinBehaviorType<T> type = new PinBehaviorType<>(id, codec, configFields, color, deprecated);
        if (REGISTRY.put(id, type) != null) {
            throw new IllegalStateException("Duplicate pin behavior registration: " + id);
        }
        return type;
    }

    @Nullable
    public static PinBehaviorType<?> byId(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Map<ResourceLocation, PinBehaviorType<?>> getAll() {
        return Map.copyOf(REGISTRY);
    }

    public static Map<ResourceLocation, PinBehaviorType<?>> getActive() {
        Map<ResourceLocation, PinBehaviorType<?>> active = new HashMap<>();
        for (Map.Entry<ResourceLocation, PinBehaviorType<?>> entry : REGISTRY.entrySet()) {
            if (!entry.getValue().deprecated()) {
                active.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(active);
    }

    @SuppressWarnings({"deprecation", "removal"})
    public static void bootstrap() {
        touch(ExplosiveBehavior.TYPE);
        touch(SpawnMobBehavior.TYPE);
        touch(MobSpawnPointBehavior.TYPE);
        touch(SeatMobBehavior.TYPE);
        touch(RopeConnectorBehavior.TYPE);
        touch(BurnTimeBehavior.TYPE);
        touch(BalloonFillerBehavior.TYPE);
    }

    private static void touch(PinBehaviorType<?> type) {
        // no-op; used referencing the type is used to trigger deferred registration
    }
}
