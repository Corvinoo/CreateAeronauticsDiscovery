package me.corvino.aeronauticsdiscovery.event;

import dev.eriksonn.aeronautics.Aeronautics;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import me.corvino.aeronauticsdiscovery.assembly.Pipelines;
import me.corvino.aeronauticsdiscovery.assembly.queue.AssemblyQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Debug;

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
        final int MaxAttempt = 5;
        SpawnPosition spawnPos = SpawnPosition.builder()
                .center(player.blockPosition())
                .altitudeRange(config.minAltitude(), config.maxAltitude())
                .horizontalDistance(offsetFromViewDistance(level))
                .facing(player.blockPosition())
                .maxAttempts(MaxAttempt)
                .constrain(SpawnPosition.noObstaclesInFront(offsetFromViewDistance(level) * 2))
                .build(level, random);

        
        if(spawnPos != null) {
            spawnAtPosition(level, config, spawnPos);
            CreateAeronauticsDiscovery.LOGGER.debug("Found good spawn point for flyover at {}", spawnPos.pos());
        }
        else {
            CreateAeronauticsDiscovery.LOGGER.debug("Could not find good spawn point in {} attempts for flyover, skipping", MaxAttempt);
        }
    }

    public static void spawnAtPosition(
            ServerLevel level, FlyoverEventConfig config, SpawnPosition spawnPos
    ) {
        if (isFlatWorld(level)) {
            CreateAeronauticsDiscovery.LOGGER.warn("[FLYOVER] Skipping flyover in flat world");
            return;
        }

        AssemblyContext ctx = AssemblyContext.builder(level, config.template(), AssemblySource.FLYOVER)
                .anchor(spawnPos.pos())
                .rotationTemplate(Rotation.NONE)
                .setYaw(spawnPos.yawRadians())
                .overrideVelocity(config.velocity())
                .maxRetries(3)
                .setName("flyover")
                .registerFlyover()
                .build();

        AssemblyQueue.get(level).enqueue(Pipelines.FLYOVER, ctx);

        CreateAeronauticsDiscovery.LOGGER.debug("[FLYOVER] Enqueued '{}' at {}", config.template(), spawnPos.pos());
    }

    private static int offsetFromViewDistance(ServerLevel level) {
        int viewDist = level.getServer().getPlayerList().getViewDistance();
        int maxDist = viewDist * 16;
        return maxDist + Config.flyoverMaxUnloadDistance / 2;
    }

    static boolean isFlatWorld(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        if (generator instanceof FlatLevelSource) return true;
        return generator.getClass().getName().equals(FlatLevelSource.class.getName());
    }
}
