package me.corvino.aeronauticsdiscovery;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
@EventBusSubscriber(modid = CreateAeronauticsDiscovery.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    
    private static final ModConfigSpec.IntValue FLYOVER_MAX_LIFETIME = BUILDER
            .comment("Maximum lifetime of a flyover in ticks before it despawns (20 ticks = 1 second)")
            .defineInRange("flyover.maxLifetimeTicks", 18000, 1, Integer.MAX_VALUE);
    

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int flyoverMaxLifetimeTicks;
    

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        flyoverMaxLifetimeTicks = FLYOVER_MAX_LIFETIME.get();
    }
}
