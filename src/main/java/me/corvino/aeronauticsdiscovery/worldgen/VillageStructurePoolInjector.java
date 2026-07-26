package me.corvino.aeronauticsdiscovery.worldgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.corvino.aeronauticsdiscovery.mixin.accessor.StructureTemplatePoolAccessor;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.GEN;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VillageStructurePoolInjector {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "village_pool_injections";

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        ResourceManager resourceManager = server.getResourceManager();
        RegistryAccess registries = server.registryAccess();

        List<VillagePoolInjectionConfig> configs = loadConfigs(resourceManager);
        if (configs.isEmpty()) {
            ModLog.info(GEN, "No village pool injection configs found");
            return;
        }

        var templatePoolRegistry = registries.registryOrThrow(Registries.TEMPLATE_POOL);
        var processorRegistry = registries.registryOrThrow(Registries.PROCESSOR_LIST);

        Map<ResourceLocation, List<VillagePoolInjectionConfig>> byPool = new HashMap<>();
        for (VillagePoolInjectionConfig config : configs) {
            byPool.computeIfAbsent(config.targetPool(), k -> new ArrayList<>()).add(config);
        }

        for (Map.Entry<ResourceLocation, List<VillagePoolInjectionConfig>> entry : byPool.entrySet()) {
            ResourceLocation poolId = entry.getKey();
            List<VillagePoolInjectionConfig> poolConfigs = entry.getValue();

            var poolKey = ResourceKey.create(Registries.TEMPLATE_POOL, poolId);
            var poolHolder = templatePoolRegistry.getHolder(poolKey);
            if (poolHolder.isEmpty()) {
                ModLog.warn(GEN, "Could not find template pool '{}', skipping {} injection(s)", poolId, poolConfigs.size());
                continue;
            }

            StructureTemplatePool pool = poolHolder.get().value();
            StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;

            ObjectArrayList<StructurePoolElement> newTemplates = new ObjectArrayList<>(accessor.getTemplates());
            ArrayList<Pair<StructurePoolElement, Integer>> newRawTemplates = new ArrayList<>(accessor.getRawTemplates());

            for (VillagePoolInjectionConfig config : poolConfigs) {
                var processorKey = ResourceKey.create(Registries.PROCESSOR_LIST, config.processor());
                var processorHolder = processorRegistry.getHolder(processorKey);
                if (processorHolder.isEmpty()) {
                    ModLog.warn(GEN, "Could not find processor list '{}', using minecraft:empty for template '{}'",
                            config.processor(), config.template());
                    var emptyKey = ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.parse("minecraft:empty"));
                    processorHolder = processorRegistry.getHolder(emptyKey);
                    if (processorHolder.isEmpty()) {
                        ModLog.error(GEN, "Could not find fallback processor list, skipping injection of '{}'", config.template());
                        continue;
                    }
                }

                StructureTemplatePool.Projection projection = parseProjection(config.projection());

                StructurePoolElement element = StructurePoolElement.legacy(
                        config.template().toString(), processorHolder.get()
                ).apply(projection);

                for (int i = 0; i < config.weight(); i++) {
                    newTemplates.add(element);
                }
                newRawTemplates.add(Pair.of(element, config.weight()));

                ModLog.debug(GEN, "Queued injection: {} -> {} (weight={}, projection={})",
                        config.template(), poolId, config.weight(), config.projection());
            }

            accessor.setTemplates(newTemplates);
            accessor.setRawTemplates(newRawTemplates);

            ModLog.info(GEN, "Injected into pool '{}': {} element(s)", poolId, poolConfigs.size());
        }
    }

    private static List<VillagePoolInjectionConfig> loadConfigs(ResourceManager resourceManager) {
        List<VillagePoolInjectionConfig> configs = new ArrayList<>();
        Map<ResourceLocation, Resource> allResources = resourceManager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json"));

        for (Map.Entry<ResourceLocation, Resource> entry : allResources.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try (Reader reader = new InputStreamReader(entry.getValue().open())) {
                JsonElement json = GSON.fromJson(reader, JsonElement.class);
                if (json == null) continue;
                VillagePoolInjectionConfig config = VillagePoolInjectionConfig.CODEC
                        .decode(JsonOps.INSTANCE, json)
                        .getOrThrow(error -> new RuntimeException("Failed to decode " + fileId + ": " + error))
                        .getFirst();
                configs.add(config);
                ModLog.debug(GEN, "Loaded village pool injection from '{}': {} -> {}",
                        fileId, config.template(), config.targetPool());
            } catch (Exception e) {
                ModLog.error(GEN, "Failed to load village pool injection from '{}': {}", fileId, e.getMessage());
            }
        }

        ModLog.info(GEN, "Loaded {} village pool injection config(s)", configs.size());
        return configs;
    }

    private static StructureTemplatePool.Projection parseProjection(String name) {
        return switch (name.toLowerCase()) {
            case "terrain_matching" -> StructureTemplatePool.Projection.TERRAIN_MATCHING;
            default -> StructureTemplatePool.Projection.RIGID;
        };
    }
}
