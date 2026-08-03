package me.corvino.aeronauticsdiscovery.autopilot;

import com.mojang.serialization.MapCodec;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.autopilot.goals.AltitudeGoal;
import me.corvino.aeronauticsdiscovery.autopilot.goals.StraightFlightGoal;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of {@link AutopilotGoalType}s. Goals are serialized by their type id in datapack goal
 * sets, so adding a new goal only requires implementing the goal + registering a type here
 * ({@link #bootstrap()} in commonSetup forces the registrations).
 */
public final class AutopilotGoalTypes {

    private static final Map<ResourceLocation, AutopilotGoalType<?>> REGISTRY = new HashMap<>();

    private AutopilotGoalTypes() {
    }

    public static <T extends AutopilotGoal<T>> AutopilotGoalType<T> register(String path, MapCodec<T> codec) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CreateAeronauticsDiscovery.MODID, path);
        AutopilotGoalType<T> type = new AutopilotGoalType<>(id, codec);
        if (REGISTRY.put(id, type) != null) {
            throw new IllegalStateException("Duplicate autopilot goal type registration: " + id);
        }
        return type;
    }

    static MapCodec<? extends AutopilotGoal<?>> codecFor(ResourceLocation id) {
        AutopilotGoalType<?> type = REGISTRY.get(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown autopilot goal type: " + id);
        }
        return type.codec();
    }

    public static void bootstrap() {
        touch(StraightFlightGoal.TYPE);
        touch(AltitudeGoal.TYPE);
    }

    private static void touch(AutopilotGoalType<?> type) {
        // no-op; forces deferred class-loading so the TYPE registrations above run
    }
}
