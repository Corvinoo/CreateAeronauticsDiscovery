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
        return register(path, codec, List.of(), color);
    }

    public static <T extends PinBehavior<T>> PinBehaviorType<T> register(String path, Codec<T> codec, List<ConfigField> configFields, int color) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CreateAeronauticsDiscovery.MODID, path);
        PinBehaviorType<T> type = new PinBehaviorType<>(id, codec, configFields, color);
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

    public static void bootstrap() {
        touch(ExplosiveBehavior.TYPE);
        touch(MobSpawnPointBehavior.TYPE);
        touch(SeatMobBehavior.TYPE);
        touch(RopeConnectorBehavior.TYPE);
        touch(BurnTimeBehavior.TYPE);
    }

    private static void touch(PinBehaviorType<?> type) {
        // no-op; used referencing the type is used to trigger deferred registration
    }
}
