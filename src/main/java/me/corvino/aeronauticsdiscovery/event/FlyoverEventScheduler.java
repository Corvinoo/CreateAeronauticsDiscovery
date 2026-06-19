package me.corvino.aeronauticsdiscovery.event;

import dev.simulated_team.simulated.util.SimAssemblyHelper;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

public final class FlyoverEventScheduler {
    private static final Map<ResourceLocation, Integer> WORLD_COOLDOWNS = new HashMap<>();
    private static final Random RANDOM = new Random();
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

        ResourceLocation dimensionKey = level.dimension().location();
        int cooldown = WORLD_COOLDOWNS.getOrDefault(dimensionKey, 0);
        if (cooldown > 0) {
            WORLD_COOLDOWNS.put(dimensionKey, cooldown - 1);
            return;
        }

        trySpawn(level, dimensionKey);
    }

    public static SimAssemblyHelper.AssemblyResult spawnForPlayer(
            ServerLevel level, FlyoverEventConfig config, ServerPlayer player, Random random
    ) throws Exception {
        if (isFlatWorld(level)) {
            throw new IllegalStateException("Flyover events are not available in superflat worlds.");
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
                player.getBlockX() + dx,
                altitude,
                player.getBlockZ() + dz
        );

        double theta = Math.atan2(player.getZ() - spawnPos.getZ(), player.getX() - spawnPos.getX());
        double yawRadians = -theta - Math.PI / 2;

        SimAssemblyHelper.AssemblyResult result = FlyoverSpawner.spawn(level, config, spawnPos, yawRadians);
        return result;
    }

    private static boolean isFlatWorld(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        if (generator instanceof FlatLevelSource) return true;
        // Fallback: compare by class name in case of classloader wrapping
        if (generator.getClass().getName().equals(FlatLevelSource.class.getName())) return true;
        return false;
    }

    private static void trySpawn(ServerLevel level, ResourceLocation dimensionKey) {
        if (!ENABLED) return;
        if (isFlatWorld(level)) return;

        FlyoverEventRegistry registry = FlyoverEventRegistry.getInstance();
        if (registry == null) return;
        List<FlyoverEventConfig> configs = registry.getConfigs();
        if (configs.isEmpty()) return;

        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        ServerPlayer player = players.get(RANDOM.nextInt(players.size()));

        FlyoverEventConfig config = registry.pickRandom(RANDOM);
        if (config == null) return;

        try {
            spawnForPlayer(level, config, player, RANDOM);
            CreateAeronauticsDiscovery.LOGGER.info("[FLYOVER] Spawned '{}' near player {}",
                    config.template(), player.getName().getString());
        } catch (Exception e) {
            CreateAeronauticsDiscovery.LOGGER.warn("[FLYOVER] Failed to spawn '{}': {}", config.template(), e.getMessage());
        }

        int adjustedCooldown = config.cooldownTicks() + RANDOM.nextInt(config.cooldownTicks() / 2 + 1);
        WORLD_COOLDOWNS.put(dimensionKey, adjustedCooldown);
    }
}
