package me.corvino.aeronauticsdiscovery.event;

import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public final class MacroChunkTracker {

    private static final Map<ResourceLocation, Map<UUID, Integer>> PLAYER_COOLDOWNS =
            new HashMap<>();


    private static final Map<ResourceLocation, Map<Long, Integer>> CHUNK_SPAWN_CDS =
            new HashMap<>();

    private static final Random RANDOM = new Random();

    private MacroChunkTracker() {}


    public static void tick(ServerLevel level) {
        ResourceLocation   dimKey    = level.dimension().location();
        int                chunkSize = Config.macroChunkSize;
        Map<UUID, Integer> playerCDs = PLAYER_COOLDOWNS.computeIfAbsent(dimKey, k -> new HashMap<>());
        Map<Long, Integer> chunkCDs  = CHUNK_SPAWN_CDS.computeIfAbsent(dimKey, k -> new HashMap<>());

        //Tick chunk rate-limiters globally, independent of player presence
        chunkCDs.entrySet().removeIf(e -> {
            int next = e.getValue() - 1;
            if (next <= 0) return true; //expired: remove entry
            e.setValue(next);
            return false;
        });

        Set<UUID> online = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            UUID uuid = player.getUUID();
            online.add(uuid);

            int cd = playerCDs.getOrDefault(uuid, -1);

            // First time we see this player in this dimension: randomise the
            // initial delay so a fresh server doesn't fire for everyone at once
            if (cd < 0) {
                int seed = Config.flyoverCooldownTicks / 2
                        + RANDOM.nextInt(Config.flyoverCooldownTicks / 2 + 1);
                playerCDs.put(uuid, seed);
                continue;
            }

            if (cd > 0) {
                playerCDs.put(uuid, cd - 1);
                continue;
            }

            // cd == 0: player is eligible.  Check chunk rate-limit
            long chunkKey = getChunkKey(player.blockPosition(), chunkSize);
            if (chunkCDs.containsKey(chunkKey)) {
                // Another player already spawned here recently
                // This player stays at cd = 0 and retries next tick, no data written
                continue;
            }

            trySpawn(level, player, chunkKey, playerCDs, chunkCDs);
        }

        // Drop entries for players who left this dimension or logged off
        playerCDs.keySet().retainAll(online);
        if (playerCDs.isEmpty()) PLAYER_COOLDOWNS.remove(dimKey);
        if (chunkCDs.isEmpty()) CHUNK_SPAWN_CDS.remove(dimKey);
    }


    private static void trySpawn(
            ServerLevel        level,
            ServerPlayer       player,
            long               chunkKey,
            Map<UUID, Integer> playerCDs,
            Map<Long, Integer> chunkCDs
    ) {
        FlyoverEventRegistry registry = FlyoverEventRegistry.getInstance();
        if (registry == null || registry.getConfigs().isEmpty()) {
            // Registry not ready: leave player at cd = 0, retry next tick
            return;
        }

        FlyoverEventConfig config = registry.pickRandom(RANDOM);
        if (config == null) return;

        boolean spawned = false;
        try {
            FlyoverEventScheduler.spawnForPlayer(level, config, player, RANDOM);
            spawned = true;
            CreateAeronauticsDiscovery.LOGGER.info(
                    "[FLYOVER] Spawned '{}' in macro chunk {} near player {}",
                    config.template(), chunkKey, player.getName().getString());
        } catch (Exception e) {
            CreateAeronauticsDiscovery.LOGGER.warn(
                    "[FLYOVER] Failed to spawn '{}' near '{}': {}",
                    config.template(), player.getName().getString(), e.getMessage());
        }

        if (spawned) {
            // Arm the chunk rate-limiter so the next eligible player in this area
            // waits rather than spawning a second plane on the same tick or shortly after.
            chunkCDs.put(chunkKey, Config.flyoverCooldownTicks/2);
        }

        // Always advance the player's personal cooldown after an eligible attempt
        // so a persistently broken spawn path doesn't lock them at cd = 0 forever.
        int base     = Config.flyoverCooldownTicks;
        int adjusted = base + RANDOM.nextInt(base / 2 + 1);
        playerCDs.put(player.getUUID(), adjusted);
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

    public static Map<UUID, Integer> getPlayerCooldowns(ServerLevel level) {
        return Collections.unmodifiableMap(
                PLAYER_COOLDOWNS.getOrDefault(level.dimension().location(), Map.of()));
    }

    public static Map<Long, Integer> getChunkSpawnCooldowns(ServerLevel level) {
        return Collections.unmodifiableMap(
                CHUNK_SPAWN_CDS.getOrDefault(level.dimension().location(), Map.of()));
    }
}