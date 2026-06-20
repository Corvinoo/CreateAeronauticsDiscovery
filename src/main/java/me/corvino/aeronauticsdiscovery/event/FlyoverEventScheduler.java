package me.corvino.aeronauticsdiscovery.event;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Random;

public final class FlyoverEventScheduler {
    private static boolean ENABLED = true;

    private FlyoverEventScheduler() {}

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static boolean toggleEnabled() {
        ENABLED = !ENABLED;
        return ENABLED;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!ENABLED) return;

        MacroChunkTracker.tick(level);
    }

    public static void spawnForPlayer(
            ServerLevel level, FlyoverEventConfig config, ServerPlayer player, Random random
    ) {
        spawnAtPosition(level, config, player.blockPosition(), random);
    }

    public static void spawnAtPosition(
            ServerLevel level, FlyoverEventConfig config, BlockPos centerPos, Random random
    ) {
        if (isFlatWorld(level)) {
            CreateAeronauticsDiscovery.LOGGER.warn("[FLYOVER] Skipping flyover in flat world");
            return;
        }

        int altitude = config.minAltitude();
        if (config.maxAltitude() > config.minAltitude()) {
            altitude += random.nextInt(config.maxAltitude() - config.minAltitude());
        }

        int viewDist = level.getServer().getPlayerList().getViewDistance();
        int maxDist = viewDist * 16;
        int offset = Math.max(48, maxDist - 24 + random.nextInt(17) - 8);

        double angle = random.nextDouble() * 2 * Math.PI;

        int dx = (int) (Math.cos(angle) * offset);
        int dz = (int) (Math.sin(angle) * offset);

        BlockPos spawnPos = new BlockPos(
                centerPos.getX() + dx,
                altitude,
                centerPos.getZ() + dz
        );

        double theta = Math.atan2(centerPos.getZ() - spawnPos.getZ(), centerPos.getX() - spawnPos.getX());
        double yawRadians = -theta - Math.PI / 2;

        AssemblyContext ctx = AssemblyContext.builder(level, config.template(), AssemblySource.FLYOVER)
                .anchor(spawnPos)
                .rotation(net.minecraft.world.level.block.Rotation.NONE)
                .yawRadians(yawRadians)
                .flyoverConfig(config)
                .velocityOverride(config.velocity())
                .activationDistance(128)
                .maxRetries(20)
                .build();

        AssemblyQueue.get(level).enqueue(Pipelines.STANDARD, ctx);

        CreateAeronauticsDiscovery.LOGGER.info("[FLYOVER] Enqueued '{}' at {}", config.template(), spawnPos);
    }

    static boolean isFlatWorld(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        if (generator instanceof FlatLevelSource) return true;
        if (generator.getClass().getName().equals(FlatLevelSource.class.getName())) return true;
        return false;
    }
}
