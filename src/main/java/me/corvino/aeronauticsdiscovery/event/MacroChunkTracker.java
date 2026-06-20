package me.corvino.aeronauticsdiscovery.event;

import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public final class MacroChunkTracker {
    private static final Map<ResourceLocation, Map<Long, Integer>> CHUNK_COOLDOWNS = new HashMap<>();
    private static final Random RANDOM = new Random();

    private MacroChunkTracker() {}

    public static void tick(ServerLevel level) {
        ResourceLocation dimKey = level.dimension().location();
        int chunkSize = Config.macroChunkSize;

        Map<Long, List<ServerPlayer>> playersByChunk = new HashMap<>();
        for (ServerPlayer player : level.players()) {
            playersByChunk.computeIfAbsent(
                    getChunkKey(player.blockPosition(), chunkSize), k -> new ArrayList<>()
            ).add(player);
        }

        if (playersByChunk.isEmpty()) return;

        Map<Long, Integer> cooldowns = CHUNK_COOLDOWNS.computeIfAbsent(dimKey, k -> new HashMap<>());

        for (Map.Entry<Long, List<ServerPlayer>> entry : playersByChunk.entrySet()) {
            Long chunkKey = entry.getKey();
            if (!cooldowns.containsKey(chunkKey)) {
                int seed = Config.flyoverCooldownTicks / 2
                        + RANDOM.nextInt(Config.flyoverCooldownTicks / 2 + 1);
                cooldowns.put(chunkKey, seed);
                continue;
            }
            int cd = cooldowns.get(chunkKey);
            if (cd > 0) {
                cooldowns.put(chunkKey, cd - 1);
                continue;
            }
            ServerPlayer player = entry.getValue().get(RANDOM.nextInt(entry.getValue().size()));
            trySpawn(level, player, chunkKey, cooldowns);
        }

        cooldowns.keySet().retainAll(playersByChunk.keySet());
        if (cooldowns.isEmpty()) {
            CHUNK_COOLDOWNS.remove(dimKey);
        }
    }

    private static void trySpawn(ServerLevel level, ServerPlayer player, Long chunkKey, Map<Long, Integer> cooldowns) {
        FlyoverEventRegistry registry = FlyoverEventRegistry.getInstance();
        if (registry == null || registry.getConfigs().isEmpty()) return;

        FlyoverEventConfig config = registry.pickRandom(RANDOM);
        if (config == null) return;

        try {
            FlyoverEventScheduler.spawnForPlayer(level, config, player, RANDOM);
            CreateAeronauticsDiscovery.LOGGER.info("[FLYOVER] Spawned '{}' in macro chunk {} near player {}", config.template(), chunkKey, player.getName().getString());
        } catch (Exception e) {
            CreateAeronauticsDiscovery.LOGGER.warn("[FLYOVER] Failed to spawn '{}' near player '{}': {}", config.template(), player.getName().getString(), e.getMessage());
        }

        int base = Config.flyoverCooldownTicks;
        int adjusted = base + RANDOM.nextInt(base / 2 + 1);
        cooldowns.put(chunkKey, adjusted);
    }

    public static long getChunkKey(BlockPos pos, int chunkSize) {
        int cx = Math.floorDiv(pos.getX(), chunkSize);
        int cz = Math.floorDiv(pos.getZ(), chunkSize);
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static BlockPos getChunkCenter(long key, int chunkSize) {
        int cx = (int) (key >> 32);
        int cz = (int) key;
        return new BlockPos(cx * chunkSize + chunkSize / 2, 0, cz * chunkSize + chunkSize / 2);
    }

    public static Map<Long, Integer> getChunkCooldowns(ServerLevel level) {
        return CHUNK_COOLDOWNS.getOrDefault(level.dimension().location(), Map.of());
    }
}
