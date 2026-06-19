package me.corvino.aeronauticsdiscovery.physics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PrefabPhysicsRegistry extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "structure_physics";

    private static PrefabPhysicsRegistry instance;

    private Map<ResourceLocation, PrefabPhysicsConfig> configs = Map.of();

    public PrefabPhysicsRegistry() {
        super(GSON, DIRECTORY);
        instance = this;
    }

    public static PrefabPhysicsRegistry getInstance() {
        return instance;
    }

    public Optional<PrefabPhysicsConfig> get(ResourceLocation templateId) {
        return Optional.ofNullable(configs.get(templateId));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, PrefabPhysicsConfig> map = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                PrefabPhysicsConfig config = PrefabPhysicsConfig.CODEC.codec().decode(JsonOps.INSTANCE, entry.getValue())
                        .getOrThrow(error -> new RuntimeException("Failed to decode " + fileId + ": " + error))
                        .getFirst();

                ResourceLocation template = config.template();

                if (map.containsKey(template)) {
                    CreateAeronauticsDiscovery.LOGGER.warn("[PHYSICS] Duplicate config for template '{}' (file '{}'), overriding", template, fileId);
                }

                map.put(template, config);
                CreateAeronauticsDiscovery.LOGGER.debug("[PHYSICS] Loaded config for template '{}' from '{}': velocity={}, impulse={}",
                        template, fileId, config.initialVelocity().linear(), config.initialVelocity().impulse());
            } catch (Exception e) {
                CreateAeronauticsDiscovery.LOGGER.error("[PHYSICS] Failed to load config from '{}': {}", fileId, e.getMessage());
            }
        }

        this.configs = Map.copyOf(map);
        CreateAeronauticsDiscovery.LOGGER.info("[PHYSICS] Loaded {} prefab physics config(s)", map.size());
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PrefabPhysicsRegistry());
    }
}
