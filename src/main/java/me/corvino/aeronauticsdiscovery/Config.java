package me.corvino.aeronauticsdiscovery;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = CreateAeronauticsDiscovery.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    
    private static final ModConfigSpec.IntValue FLYOVER_MAX_LIFETIME = BUILDER
            .comment("Maximum lifetime of a flyover in ticks before it despawns (20 ticks = 1 second)")
            .defineInRange("flyover.maxLifetimeTicks", 6000, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue FLYOVER_COOLDOWN = BUILDER
            .comment("Base cooldown between flyover spawns per macro chunk (20 ticks = 1 second)")
            .defineInRange("flyover.baseCooldownTicks", 12000, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue MACRO_CHUNK_SIZE = BUILDER
            .comment("Length in blocks of each macro chunk.")
            .defineInRange("flyover.macroChunkSize", 128, 16, 512);

    private static final ModConfigSpec.IntValue FLYOVER_MAX_UNLOAD_DISTANCE = BUILDER
            .comment("Extra distance in blocks beyond render distance at which a flyover is force-despawned. "
                    + "Set to 0 to despawn exactly at render distance edge.")
            .defineInRange("flyover.maxUnloadDistance", 64, 0, 1024);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int flyoverMaxLifetimeTicks;
    public static int flyoverCooldownTicks;
    public static int macroChunkSize;
    public static int flyoverMaxUnloadDistance;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        flyoverMaxLifetimeTicks = FLYOVER_MAX_LIFETIME.get();
        flyoverCooldownTicks = FLYOVER_COOLDOWN.get();
        macroChunkSize = MACRO_CHUNK_SIZE.get();
        flyoverMaxUnloadDistance = FLYOVER_MAX_UNLOAD_DISTANCE.get();
    }
}
