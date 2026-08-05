package me.corvino.aeronauticsdiscovery.patrol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.PATROL;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Loads every {@code data/<ns>/patrols/*.json} into an immutable list of {@link PatrolConfig}s. */
public class PatrolRegistry extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "patrols";

    private static PatrolRegistry instance;

    private List<PatrolConfig> configs = List.of();

    public PatrolRegistry() {
        super(GSON, DIRECTORY);
        instance = this;
    }

    public static PatrolRegistry getInstance() {
        return instance;
    }

    public List<PatrolConfig> getConfigs() {
        return configs;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        List<PatrolConfig> list = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            try {
                PatrolConfig config = PatrolConfig.CODEC.codec()
                        .decode(JsonOps.INSTANCE, entry.getValue())
                        .getOrThrow(error -> new RuntimeException("Failed to decode " + entry.getKey() + ": " + error))
                        .getFirst();
                list.add(config);
                ModLog.debug(PATROL, "Loaded patrol for template '{}' targeting '{}' (weight={}) from '{}'",
                        config.template(), config.targetStructure(), config.weight(), entry.getKey());
            } catch (Exception e) {
                ModLog.error(PATROL, "Failed to load patrol config from '{}': {}", entry.getKey(), e.getMessage());
            }
        }
        this.configs = List.copyOf(list);
        ModLog.info(PATROL, "Loaded {} patrol config(s)", list.size());
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PatrolRegistry());
    }
}
