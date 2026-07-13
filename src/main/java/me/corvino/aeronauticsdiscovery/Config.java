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

    private static final ModConfigSpec.BooleanValue FLYOVER_OBSTACLE_AVOIDANCE = BUILDER
            .comment("When enabled, flyovers try to avoid spawning position where a block wall exists between "
                    + "the spawn point and the player. May introduce a minor performance overhead")
            .define("flyover.obstacleCheck", false);

    private static final ModConfigSpec.DoubleValue IMPACT_STRENGTH_THRESHOLD = BUILDER
            .comment("Minimum impact velocity (m/s) along the contact normal to fire a SubLevelImpactEvent. "
                    + "Set to 0 to fire on every collision.")
            .defineInRange("impact.strengthThreshold", 5.0, 0.0, 1000.0);

    private static final ModConfigSpec.BooleanValue EXPLOSION_BLOCKS = BUILDER
            .comment("Whether explosive pins destroy blocks on detonation.")
            .define("impact.explosion.blocks", true);

    private static final ModConfigSpec.BooleanValue EXPLOSION_FIRE = BUILDER
            .comment("Whether explosions can create fire")
            .define("impact.explosion.fire", false);

    private static final ModConfigSpec.EnumValue<ExplosionMode> EXPLOSION_STRATEGY = BUILDER
            .comment("Explosion mode: OPTIMIZED (no raycasting) or VANILLA (more accurate, can be very slow on sublevels).")
            .defineEnum("impact.explosion.strategy", ExplosionMode.OPTIMIZED);

    private static final ModConfigSpec.IntValue TRADER_ANGER_DURATION = BUILDER
            .comment("How long the SoaringTrader stays angry (prices doubled) after a crash or being hit, in ticks (20 ticks = 1 second).")
            .defineInRange("trader.angerDurationTicks", 12000, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue TRADER_GUARANTEED_MAP = BUILDER
            .comment("When enabled, the SoaringTrader will always offer a structure map as long as any structure type exists within search range. " +
                    "When disabled, it picks one random structure type and only offers a map if that specific type is found.")
            .define("trader.guaranteedMap", true);

    private static final ModConfigSpec.BooleanValue PROCESS_ALL_SUBLEVEL = BUILDER.
            comment("If enabled, all sublevels including the ones NOT created by this mod will be processed for internal physics detection."+ 
                    "\n This makes event triggers such as 'External Force' pins work regardless of the sublevel internal data. " +
                    " \n Useful while building using Pins.")
            .define("general.processAll", false);

    private static final ModConfigSpec.BooleanValue FRAGMENT_PROMOTION = BUILDER
            .comment("When enabled, if the player approaches a flyover fragment it will not despawn (NOTE: with large amounts of fragments this may cause a performance overhead).")
            .define("flyover.promotion.enabled", false);

    private static final ModConfigSpec.DoubleValue PROMOTION_RANGE = BUILDER
            .comment("Range in blocks from the fragment at which promotion triggers.")
            .defineInRange("flyover.promotion.range", 10.0, 1.0, 128.0);
    
    static final ModConfigSpec SPEC = BUILDER.build();

    public static int flyoverMaxLifetimeTicks;
    public static int flyoverCooldownTicks;
    public static int macroChunkSize;
    public static int flyoverMaxUnloadDistance;
    public static boolean flyoverObstacleCheck;
    public static double impactStrengthThreshold;
    public static boolean explosionBlocks;
    public static boolean explosionFire;
    public static ExplosionMode explosionStrategy;
    public static int traderAngerDuration;
    public static boolean traderGuaranteedMap;
    public static boolean processAllSublevels;
    public static boolean fragmentPromotion;
    public static double promotionRange;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        flyoverMaxLifetimeTicks = FLYOVER_MAX_LIFETIME.get();
        flyoverCooldownTicks = FLYOVER_COOLDOWN.get();
        macroChunkSize = MACRO_CHUNK_SIZE.get();
        flyoverMaxUnloadDistance = FLYOVER_MAX_UNLOAD_DISTANCE.get();
        flyoverObstacleCheck = FLYOVER_OBSTACLE_AVOIDANCE.get();
        impactStrengthThreshold = IMPACT_STRENGTH_THRESHOLD.get();
        explosionBlocks = EXPLOSION_BLOCKS.get();
        explosionFire = EXPLOSION_FIRE.get();
        explosionStrategy = EXPLOSION_STRATEGY.get();
        traderAngerDuration = TRADER_ANGER_DURATION.get();
        traderGuaranteedMap = TRADER_GUARANTEED_MAP.get();
        processAllSublevels = PROCESS_ALL_SUBLEVEL.get();
        fragmentPromotion = FRAGMENT_PROMOTION.get();
        promotionRange = PROMOTION_RANGE.get();
    }

    public enum ExplosionMode {
        OPTIMIZED,
        VANILLA
    }
}
