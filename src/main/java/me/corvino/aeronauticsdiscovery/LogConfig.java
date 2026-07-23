package me.corvino.aeronauticsdiscovery;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = CreateAeronauticsDiscovery.MODID)
public class LogConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue LOG_BRIDGE = BUILDER
            .comment("Enable [BRIDGE] log output")
            .define("log.bridge", false);

    private static final ModConfigSpec.BooleanValue LOG_MIXIN = BUILDER
            .comment("Enable [MIXIN] log output")
            .define("log.mixin", false);

    private static final ModConfigSpec.BooleanValue LOG_FLYOVER = BUILDER
            .comment("Enable [FLYOVER] log output")
            .define("log.flyover", false);

    private static final ModConfigSpec.BooleanValue LOG_FLYOVER_TEST = BUILDER
            .comment("Enable [FLYOVER_TEST] log output")
            .define("log.flyoverTest", false);

    private static final ModConfigSpec.BooleanValue LOG_PHYSICS = BUILDER
            .comment("Enable [PHYSICS] log output")
            .define("log.physics", false);

    private static final ModConfigSpec.BooleanValue LOG_BUOYANCY = BUILDER
            .comment("Enable [BUOYANCY] log output")
            .define("log.buoyancy", false);

    private static final ModConfigSpec.BooleanValue LOG_QUEUE = BUILDER
            .comment("Enable [QUEUE] log output")
            .define("log.queue", false);

    private static final ModConfigSpec.BooleanValue LOG_TRADE = BUILDER
            .comment("Enable [TRADE] log output")
            .define("log.trade", false);

    private static final ModConfigSpec.BooleanValue LOG_GEN = BUILDER
            .comment("Enable [GEN] log output")
            .define("log.gen", false);

    private static final ModConfigSpec.BooleanValue LOG_SEAT = BUILDER
            .comment("Enable [SEAT] log output")
            .define("log.seat", false);

    private static final ModConfigSpec.BooleanValue LOG_PIN = BUILDER
            .comment("Enable [PIN] log output")
            .define("log.pin", false);

    private static final ModConfigSpec.BooleanValue LOG_PIPELINE = BUILDER
            .comment("Enable [PIPELINE] log output")
            .define("log.pipeline", false);

    private static final ModConfigSpec.BooleanValue LOG_GENERAL = BUILDER
            .comment("Enable [GENERAL] log output")
            .define("log.general", false);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean logBridge;
    public static boolean logMixin;
    public static boolean logFlyover;
    public static boolean logFlyoverTest;
    public static boolean logPhysics;
    public static boolean logBuoyancy;
    public static boolean logQueue;
    public static boolean logTrade;
    public static boolean logGen;
    public static boolean logSeat;
    public static boolean logPin;
    public static boolean logPipeline;
    public static boolean logGeneral;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        logBridge = LOG_BRIDGE.get();
        logMixin = LOG_MIXIN.get();
        logFlyover = LOG_FLYOVER.get();
        logFlyoverTest = LOG_FLYOVER_TEST.get();
        logPhysics = LOG_PHYSICS.get();
        logBuoyancy = LOG_BUOYANCY.get();
        logQueue = LOG_QUEUE.get();
        logTrade = LOG_TRADE.get();
        logGen = LOG_GEN.get();
        logSeat = LOG_SEAT.get();
        logPin = LOG_PIN.get();
        logPipeline = LOG_PIPELINE.get();
        logGeneral = LOG_GENERAL.get();
    }
}
