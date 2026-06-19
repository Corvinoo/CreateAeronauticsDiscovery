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

        Set<Long> activeChunks = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            activeChunks.add(getChunkKey(player.blockPosition(), chunkSize));
        }

        if (activeChunks.isEmpty()) return;

        Map<Long, Integer> cooldowns = CHUNK_COOLDOWNS.computeIfAbsent(dimKey, k -> new HashMap<>());

        for (Long chunkKey : activeChunks) {
            int cd = cooldowns.getOrDefault(chunkKey, 0);
            if (cd > 0) {
                cooldowns.put(chunkKey, cd - 1);
                continue;
            }
            trySpawn(level, chunkKey, cooldowns);
        }

        cooldowns.keySet().retainAll(activeChunks);
        if (cooldowns.isEmpty()) {
            CHUNK_COOLDOWNS.remove(dimKey);
        }
    }

    private static void trySpawn(ServerLevel level, Long chunkKey, Map<Long, Integer> cooldowns) {
        FlyoverEventRegistry registry = FlyoverEventRegistry.getInstance();
        if (registry == null || registry.getConfigs().isEmpty()) return;

        FlyoverEventConfig config = registry.pickRandom(RANDOM);
        if (config == null) return;

        BlockPos center = getChunkCenter(chunkKey, Config.macroChunkSize);

        try {
            FlyoverEventScheduler.spawnAtPosition(level, config, center, RANDOM);
            CreateAeronauticsDiscovery.LOGGER.debug("[FLYOVER] Spawned '{}' in macro chunk {}", config.template(), chunkKey);
        } catch (Exception e) {
            CreateAeronauticsDiscovery.LOGGER.warn("[FLYOVER] Failed to spawn '{}': {}", config.template(), e.getMessage());
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
}
