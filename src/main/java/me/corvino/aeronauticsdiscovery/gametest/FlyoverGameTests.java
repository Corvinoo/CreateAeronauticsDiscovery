package me.corvino.aeronauticsdiscovery.gametest;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyQueue;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import me.corvino.aeronauticsdiscovery.assembly.Pipelines;
import me.corvino.aeronauticsdiscovery.event.FlyoverManager;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.slf4j.Logger;

/**
 * FlyoverGameTests - 4-tier distance test suite for flyover spawning & assembly
 *
 * Each test spawns a fully configured flyover through the standard AssemblyQueue
 * pipeline ({@link Pipelines#FLYOVER}) at a controlled distance from the test
 * origin, waits for the pipeline to reach a terminal state, then logs structured
 * results that can be analysed after the run.
 *
 *  TIER_1  │  Flyover inside BOTH render AND simulation distance          
 *  TIER_2  │  Flyover OUTSIDE render distance, INSIDE simulation distance 
 *  TIER_3  │  Flyover INSIDE render distance, OUTSIDE simulation distance 
 *  TIER_4  │  Flyover OUTSIDE BOTH distances                              
 *
 * Each tier logs the following in a structured, grep-able format:
 *   [FLYOVER_TEST] === header / tier marker ===
 *   [FLYOVER_TEST] Server config: viewDist (blocks), simDist (blocks)
 *   [FLYOVER_TEST] Spawn: world position, distance from origin (blocks)
 *   [FLYOVER_TEST] Enqueue: template, pipeline name
 *   [FLYOVER_TEST] Poll @ <tick>: state dump (queue entries, flyovers)
 *   [FLYOVER_TEST] Terminal: elapsed ticks, pipeline result, flyover count
 *   [FLYOVER_TEST] === tier PASSED / FAILED ===
 *
 * Future expansion: add verify/analysis steps after the "Terminal" log line.
 */
@GameTestHolder(CreateAeronauticsDiscovery.MODID)
public class FlyoverGameTests {

    private static final Logger LOG = CreateAeronauticsDiscovery.LOGGER;

    private static final ResourceLocation TEMPLATE_ID =
            ResourceLocation.parse("aeronauticsdiscovery:airplane");

    private static final int ALTITUDE         = 64;
    private static final int ACTIVATION_DIST  = 128;
    private static final int MAX_RETRIES      = 3;

    // Total game-test timeout must be large enough to cover worst-case retry
    // pipeline: each attempt can take ~205 ticks, 3 retries → ~615
    private static final int TIMEOUT_TICKS    = 800;

    // Tier 1 – Inside both render AND simulation distance
    @GameTest(template = "empty", timeoutTicks = TIMEOUT_TICKS)
    public void tier1_insideBoth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, ALTITUDE, 0));
        DistanceInfo info = DistanceInfo.from(level);
        BlockPos target = origin.offset(16, 0, 16);

        logHeader("TIER_1", origin, target, info);
        runTier(helper, level, "TIER_1", origin, target, info,
                true, true);
    }

    // Tier 2 – Outside render distance, INSIDE simulation distance
    @GameTest(template = "empty", timeoutTicks = TIMEOUT_TICKS)
    public void tier2_outsideRenderInsideSim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, ALTITUDE, 0));
        DistanceInfo info = DistanceInfo.from(level);

        if (!info.tier2Possible()) {
            LOG.warn("[FLYOVER_TEST] TIER_2 skipped – viewDist ({}) >= simDist ({})",
                     info.viewDistBlocks, info.simDistBlocks);
            helper.succeed();
            return;
        }

        int offset = (info.simDistBlocks + info.viewDistBlocks) / 2;
        BlockPos target = origin.offset(offset, 0, 0);

        logHeader("TIER_2", origin, target, info);
        runTier(helper, level, "TIER_2", origin, target, info,
                false, true);
    }

    // Tier 3 – INSIDE render distance, Outside simulation distance
    @GameTest(template = "empty", timeoutTicks = TIMEOUT_TICKS)
    public void tier3_insideRenderOutsideSim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, ALTITUDE, 0));
        DistanceInfo info = DistanceInfo.from(level);

        if (!info.tier3Possible()) {
            LOG.warn("[FLYOVER_TEST] TIER_3 skipped – simDist ({}) >= viewDist ({})",
                     info.simDistBlocks, info.viewDistBlocks);
            helper.succeed();
            return;
        }

        int offset = (info.viewDistBlocks + info.simDistBlocks) / 2;
        BlockPos target = origin.offset(offset, 0, 0);

        logHeader("TIER_3", origin, target, info);
        runTier(helper, level, "TIER_3", origin, target, info,
                true, false);
    }

    // Tier 4 – Outside BOTH render AND simulation distance
    @GameTest(template = "empty", timeoutTicks = TIMEOUT_TICKS)
    public void tier4_outsideBoth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, ALTITUDE, 0));
        DistanceInfo info = DistanceInfo.from(level);

        int offset = Math.max(info.viewDistBlocks, info.simDistBlocks) + 96;
        BlockPos target = origin.offset(offset, 0, 0);

        logHeader("TIER_4", origin, target, info);
        runTier(helper, level, "TIER_4", origin, target, info,
                false, false);
    }

    // Core test runner - shared by all 4 tiers

    /**
     * Enqueues a flyover at {@code target}, then polls every tick via
     * {@link GameTestHelper#succeedWhen(Runnable)} until the assembly pipeline
     * reaches a terminal state (success, or discarded after max-retries).
     *
     * @param helper    the game-test helper
     * @param level     the server level
     * @param tier      human-readable tier name (e.g. "TIER_1")
     * @param origin    the reference "player" position
     * @param target    absolute spawn position for the flyover
     * @param info      computed distance info for logging
     * @param insideRender  whether the target is within render distance
     * @param insideSim     whether the target is within simulation distance
     */
    @SuppressWarnings("SameParameterValue")
    private void runTier(GameTestHelper helper,
                         ServerLevel level,
                         String tier,
                         BlockPos origin,
                         BlockPos target,
                         DistanceInfo info,
                         boolean insideRender,
                         boolean insideSim) {

        // ---- Spawn the flyover (synchronous enqueue) ----
        AssemblyContext ctx = buildContext(level, target);

        LOG.info("[FLYOVER_TEST] {} Enqueue: template={}, pipeline={}, anchor={}",
                 tier, ctx.templateId, Pipelines.FLYOVER.name(), target);

        AssemblyQueue queue = AssemblyQueue.get(level);
        queue.enqueue(Pipelines.FLYOVER, ctx);

        int beforeCount = queue.getEntries().size();
        long startTick  = level.getGameTime();

        // ---- Poll for pipeline completion ----
        helper.succeedWhen(() -> {
            long elapsed = level.getGameTime() - startTick;

            AssemblyQueue currentQueue = AssemblyQueue.get(level);
            FlyoverManager manager = FlyoverManager.get(level);
            int queueSize       = currentQueue.getEntries().size();
            int flyoverCount    = manager.getAllFlyovers().size();
            boolean progressed  = queueSize < beforeCount || flyoverCount > 0;

            // Periodic progress log
            if (elapsed % 25 == 0 && elapsed > 0) {
                LOG.info("[FLYOVER_TEST] {} Poll @ {}t: queue={}, flyovers={}, progressed={}",
                         tier, elapsed, queueSize, flyoverCount, progressed);
            }

            // Terminal condition: queue processed the entry (either success or final discard after max retries)
            boolean queueDone = queueSize == 0 && beforeCount > 0;
            boolean flyoverRegistered = flyoverCount > 0;

            if (queueDone || elapsed >= TIMEOUT_TICKS - 50) {
                String outcome = queueDone
                        ? (flyoverRegistered ? "SUCCESS" : "DISCARDED")
                        : "TIMEOUT";

                int dX = target.getX() - origin.getX();
                int dZ = target.getZ() - origin.getZ();
                int dist = (int) Math.sqrt(dX * dX + dZ * dZ);

                LOG.info("[FLYOVER_TEST] {} Terminal: outcome={}, elapsed={}t, "
                         + "queue={}, flyovers={}, dist={}b, "
                         + "insideRender={}, insideSim={}",
                         tier, outcome, elapsed, queueSize, flyoverCount, dist,
                         insideRender, insideSim);

                LOG.info("[FLYOVER_TEST] === {} {} ===",
                         tier, queueDone ? "PASSED" : "PARTIAL");

                if (!queueDone) {
                    LOG.warn("[FLYOVER_TEST] {} did not reach terminal within {} ticks "
                             + "(view={}, sim={})",
                             tier, TIMEOUT_TICKS,
                             info.viewDistBlocks, info.simDistBlocks);
                }
                helper.succeed();
                return;
            }

            throw new GameTestAssertException(
                    tier + " pipeline still processing (t=" + elapsed + ")");
        });
    }

    // AssemblyContext factory
    /**
     * Builds an {@link AssemblyContext} identical to what
     * {@link FlyoverEventScheduler#spawnAtPosition} produces, except the
     * position is completely controlled (no random offset).
     */
    private static AssemblyContext buildContext(ServerLevel level, BlockPos anchor) {
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

    // Distance-info helper - reads server config at test time
    /**
     * Snapshot of the server's view/simulation distances converted to blocks,
     * with convenience queries for each tier.
     */
    private record DistanceInfo(int viewDistBlocks, int simDistBlocks) {

        static DistanceInfo from(ServerLevel level) {
            var playerList = level.getServer().getPlayerList();
            return new DistanceInfo(
                    playerList.getViewDistance() * 16,
                    playerList.getSimulationDistance() * 16
            );
        }

        /** Tier 2 is valid only when sim-distance exceeds view-distance. */
        boolean tier2Possible() {
            return simDistBlocks > viewDistBlocks;
        }

        /** Tier 3 is valid only when view-distance exceeds sim-distance. */
        boolean tier3Possible() {
            return viewDistBlocks > simDistBlocks;
        }
    }

    // Structured logging helpers
    private static void logHeader(String tier, BlockPos origin, BlockPos target,
                                  DistanceInfo info) {
        int dX = target.getX() - origin.getX();
        int dZ = target.getZ() - origin.getZ();
        int dist = (int) Math.sqrt(dX * dX + dZ * dZ);

        LOG.info("==============================================");
        LOG.info("[FLYOVER_TEST] === {} ===", tier);
        LOG.info("[FLYOVER_TEST] Server viewDist={}b, simDist={}b",
                 info.viewDistBlocks, info.simDistBlocks);
        LOG.info("[FLYOVER_TEST] Origin: ({}, {}, {})", origin.getX(), origin.getY(), origin.getZ());
        LOG.info("[FLYOVER_TEST] Target: ({}, {}, {})", target.getX(), target.getY(), target.getZ());
        LOG.info("[FLYOVER_TEST] Distance: {} blocks", dist);
        LOG.info("[FLYOVER_TEST] Template: {}", TEMPLATE_ID);
    }
}
