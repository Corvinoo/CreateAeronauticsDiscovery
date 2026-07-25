package me.corvino.aeronauticsdiscovery.worldgen;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.corvino.aeronauticsdiscovery.mixin.accessor.StructureTemplatePoolAccessor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;

public class VillageStructurePoolInjector {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation VILLAGE_PLAINS_HOUSES =
            ResourceLocation.parse("minecraft:village/plains/houses");

    private static final ResourceLocation VILLAGE_BALLOON =
            ResourceLocation.parse("aeronauticsdiscovery:village_balloon");

    private static final ResourceLocation EMPTY_PROCESSORS =
            ResourceLocation.parse("minecraft:empty");

    private static final int WEIGHT = 2;

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        RegistryAccess registries = server.registryAccess();

        var templatePoolKey = ResourceKey.create(Registries.TEMPLATE_POOL, VILLAGE_PLAINS_HOUSES);
        var templatePoolRegistry = registries.registryOrThrow(Registries.TEMPLATE_POOL);

        var holder = templatePoolRegistry.getHolder(templatePoolKey);
        if (holder.isEmpty()) {
            LOGGER.warn("[VillageStructurePoolInjector] Could not find template pool holder for {}", VILLAGE_PLAINS_HOUSES);
            return;
        }

        StructureTemplatePool pool = holder.get().value();
        StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;

        var processorKey = ResourceKey.create(Registries.PROCESSOR_LIST, EMPTY_PROCESSORS);
        var processorRegistry = registries.registryOrThrow(Registries.PROCESSOR_LIST);
        var emptyProcessors = processorRegistry.getHolder(processorKey);
        if (emptyProcessors.isEmpty()) {
            LOGGER.warn("[VillageStructurePoolInjector] Could not find empty processor list");
            return;
        }

        StructurePoolElement element = StructurePoolElement.legacy(
                VILLAGE_BALLOON.toString(), emptyProcessors.get()
        ).apply(StructureTemplatePool.Projection.TERRAIN_MATCHING);

        ObjectArrayList<StructurePoolElement> newTemplates = new ObjectArrayList<>(accessor.getTemplates());
        for (int i = 0; i < WEIGHT; i++) {
            newTemplates.add(element);
        }
        accessor.setTemplates(newTemplates);

        ArrayList<Pair<StructurePoolElement, Integer>> newRawTemplates = new ArrayList<>(accessor.getRawTemplates());
        newRawTemplates.add(Pair.of(element, WEIGHT));
        accessor.setRawTemplates(newRawTemplates);

        LOGGER.info("[VillageStructurePoolInjector] Injected {} into {} (weight {})",
                VILLAGE_BALLOON, VILLAGE_PLAINS_HOUSES, WEIGHT);
    }
}
