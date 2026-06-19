package me.corvino.aeronauticsdiscovery.event;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class FlyoverEventRegistry extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "flyover_events";

    private static FlyoverEventRegistry instance;

    private List<FlyoverEventConfig> configs = List.of();

    public FlyoverEventRegistry() {
        super(GSON, DIRECTORY);
        instance = this;
    }

    public static FlyoverEventRegistry getInstance() {
        return instance;
    }

    public List<FlyoverEventConfig> getConfigs() {
        return configs;
    }

    public FlyoverEventConfig pickRandom(Random random) {
        return pickRandom(configs, random);
    }

    static FlyoverEventConfig pickRandom(List<FlyoverEventConfig> configs, Random random) {
        if (configs.isEmpty()) return null;
        int totalWeight = configs.stream().mapToInt(FlyoverEventConfig::weight).sum();
        if (totalWeight <= 0) return configs.get(random.nextInt(configs.size()));
        int r = random.nextInt(totalWeight);
        int cumulative = 0;
        for (FlyoverEventConfig config : configs) {
            cumulative += config.weight();
            if (r < cumulative) return config;
        }
        return configs.getLast();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        List<FlyoverEventConfig> list = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            try {
                FlyoverEventConfig config = FlyoverEventConfig.CODEC
                        .decode(JsonOps.INSTANCE, entry.getValue())
                        .getOrThrow(error -> new RuntimeException("Failed to decode " + entry.getKey() + ": " + error))
                        .getFirst();
                list.add(config);
                CreateAeronauticsDiscovery.LOGGER.debug("[FLYOVER] Loaded config for template '{}' from '{}'", config.template(), entry.getKey());
            } catch (Exception e) {
                CreateAeronauticsDiscovery.LOGGER.error("[FLYOVER] Failed to load config from '{}': {}", entry.getKey(), e.getMessage());
            }
        }
        this.configs = List.copyOf(list);
        CreateAeronauticsDiscovery.LOGGER.info("[FLYOVER] Loaded {} flyover event config(s)", list.size());
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new FlyoverEventRegistry());
    }
}
