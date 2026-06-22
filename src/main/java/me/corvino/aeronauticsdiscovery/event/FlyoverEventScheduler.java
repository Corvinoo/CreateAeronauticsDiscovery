package me.corvino.aeronauticsdiscovery.event;

import dev.simulated_team.simulated.util.SimAssemblyHelper;
import me.corvino.aeronauticsdiscovery.PrefabService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
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

    public static SimAssemblyHelper.AssemblyResult spawnForPlayer(
            ServerLevel level, FlyoverEventConfig config, ServerPlayer player, Random random
    ) throws Exception {
        return spawnAtPosition(level, config, player.blockPosition(), random);
    }

    public static SimAssemblyHelper.AssemblyResult spawnAtPosition(
            ServerLevel level, FlyoverEventConfig config, BlockPos centerPos, Random random
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
                centerPos.getX() + dx,
                altitude,
                centerPos.getZ() + dz
        );

        double theta = Math.atan2(centerPos.getZ() - spawnPos.getZ(), centerPos.getX() - spawnPos.getX());
        double yawRadians = -theta - Math.PI / 2;


        StructureTemplate template = PrefabService.loadPrefab(level, config.template());

        Vec3i size = template.getSize();
        int radiusBlocks = Math.max(size.getX(), size.getZ()) / 2 + 16;
        int minCX = SectionPos.blockToSectionCoord(spawnPos.getX() - radiusBlocks);
        int minCZ = SectionPos.blockToSectionCoord(spawnPos.getZ() - radiusBlocks);
        int maxCX = SectionPos.blockToSectionCoord(spawnPos.getX() + radiusBlocks);
        int maxCZ = SectionPos.blockToSectionCoord(spawnPos.getZ() + radiusBlocks);

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                FlyoverManager.ticketController.forceChunk(level, spawnPos, cx, cz, true, true);
            }
        }

        SimAssemblyHelper.AssemblyResult result = FlyoverSpawner.spawn(level, config, spawnPos, yawRadians);

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                FlyoverManager.ticketController.forceChunk(level, spawnPos, cx, cz, false, true);
            }
        }

        return result;
    }

    static boolean isFlatWorld(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        if (generator instanceof FlatLevelSource) return true;
        return generator.getClass().getName().equals(FlatLevelSource.class.getName());
    }
}
