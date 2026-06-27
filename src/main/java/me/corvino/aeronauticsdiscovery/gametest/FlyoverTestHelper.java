package me.corvino.aeronauticsdiscovery.gametest;

import it.unimi.dsi.fastutil.longs.LongSet;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import java.util.List;
import java.util.Map;

public final class FlyoverTestHelper {

    public static final Logger LOG = CreateAeronauticsDiscovery.LOGGER;

    public static final ResourceLocation TEMPLATE_ID =
            ResourceLocation.parse("aeronauticsdiscovery:airplane");

    public static final int ALTITUDE         = 64;
    public static final int ACTIVATION_DIST  = 128;
    public static final int MAX_RETRIES      = 3;
    public static final int TIMEOUT_TICKS    = 800;

    // Distance configs (in chunks)
    public static final int VIEW_CHUNKS  = 10;  // 160 blocks
    public static final int SIM_CHUNKS   = 8;   // 128 blocks
    public static final int SIM_GT_VIEW  = 12;  // > VIEW_CHUNKS, for tier 2
    public static final int VIEW_GT_SIM  = 12;  // > SIM_CHUNKS, for tier 3

    private FlyoverTestHelper() {}

    public static Player spawnPlayer(GameTestHelper helper, ServerLevel level) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos origin = helper.absolutePos(new BlockPos(0, ALTITUDE, 0));
        player.teleportTo(level, origin.getX(), origin.getY(), origin.getZ(),
                          java.util.Set.of(), 0.0F, 0.0F);
        return player;
    }

    public static void configureServer(ServerLevel level, int viewChunks, int simChunks) {
        level.getServer().getPlayerList().setViewDistance(viewChunks);
        level.getServer().getPlayerList().setSimulationDistance(simChunks);
    }

    public static AssemblyContext buildContext(ServerLevel level, BlockPos anchor) {
        return AssemblyContext.builder(level, TEMPLATE_ID, AssemblySource.FLYOVER)
                .anchor(anchor)
                .rotationTemplate(Rotation.NONE)
                .setYaw(0.0)
                .overrideVelocity(InitialVelocity.NONE)
                .activationDistance(ACTIVATION_DIST)
                .maxRetries(MAX_RETRIES)
                .setName("flyover_test")
                .registerFlyover()
                .build();
    }

    public static void logHeader(String tier, BlockPos origin, BlockPos target,
                                  DistanceInfo info) {
        int dX = target.getX() - origin.getX();
        int dZ = target.getZ() - origin.getZ();
        int dist = (int) Math.sqrt(dX * dX + dZ * dZ);

        LOG.info("==============================================");
        LOG.info("[FLYOVER_TEST] === {} ===", tier);
        LOG.info("[FLYOVER_TEST] Server viewDist={}b, simDist={}b",
                 info.viewDistBlocks(), info.simDistBlocks());
        LOG.info("[FLYOVER_TEST] Origin: ({}, {}, {})", origin.getX(), origin.getY(), origin.getZ());
        LOG.info("[FLYOVER_TEST] Target: ({}, {}, {})", target.getX(), target.getY(), target.getZ());
        LOG.info("[FLYOVER_TEST] Distance: {} blocks", dist);
        LOG.info("[FLYOVER_TEST] Template: {}", TEMPLATE_ID);
    }

    // ========================================================================
    // Value types
    // ========================================================================

    public record DistanceInfo(int viewDistBlocks, int simDistBlocks) {

        public static DistanceInfo from(ServerLevel level) {
            var playerList = level.getServer().getPlayerList();
            return new DistanceInfo(
                    playerList.getViewDistance() * 16,
                    playerList.getSimulationDistance() * 16
            );
        }

        public boolean tier2Possible() {
            return simDistBlocks > viewDistBlocks;
        }

        public boolean tier3Possible() {
            return viewDistBlocks > simDistBlocks;
        }
    }

    public record TierConfig(String name, int viewChunks, int simChunks,
                              boolean insideView, boolean insideSim) {

        public static final List<TierConfig> ALL = List.of(
                new TierConfig("TIER_1", 10,  8,  true,  true),
                new TierConfig("TIER_2", 10,  12, false, true),
                new TierConfig("TIER_3", 12,  8,  true,  false),
                new TierConfig("TIER_4", 10,  8,  false, false)
        );

        public int computeTargetOffsetBlocks() {
            int viewBlocks = viewChunks * 16;
            int simBlocks  = simChunks * 16;
            if (insideView && insideSim)      return Math.min(viewBlocks, simBlocks) - 32;
            if (!insideView && insideSim)     return (simBlocks + viewBlocks) / 2;
            if (insideView && !insideSim)     return (viewBlocks + simBlocks) / 2;
            return Math.max(viewBlocks, simBlocks) + 96;
        }
    }

    public record ForcedChunksSnapshot(int vanillaForced, int blockForced, int blockTicking,
                                        int entityForced, int entityTicking) {

        public static ForcedChunksSnapshot capture(ServerLevel level) {
            ForcedChunksSavedData data = level.getDataStorage()
                    .get(ForcedChunksSavedData.factory(), "chunks");
            if (data == null) return new ForcedChunksSnapshot(0, 0, 0, 0, 0);

            return new ForcedChunksSnapshot(
                    data.getChunks().size(),
                    sumSizes(data.getBlockForcedChunks().getChunks()),
                    sumSizes(data.getBlockForcedChunks().getTickingChunks()),
                    sumSizes(data.getEntityForcedChunks().getChunks()),
                    sumSizes(data.getEntityForcedChunks().getTickingChunks())
            );
        }

        private static int sumSizes(Map<?, LongSet> map) {
            int sum = 0;
            for (LongSet set : map.values()) {
                sum += set.size();
            }
            return sum;
        }

        public int total() {
            return vanillaForced + blockForced + blockTicking + entityForced + entityTicking;
        }

        @Override
        public String toString() {
            return String.format("v=%d, b=%d, bt=%d, e=%d, et=%d (total=%d)",
                    vanillaForced, blockForced, blockTicking, entityForced, entityTicking, total());
        }
    }
}
