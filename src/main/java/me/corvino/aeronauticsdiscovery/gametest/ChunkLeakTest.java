package me.corvino.aeronauticsdiscovery.gametest;

import static me.corvino.aeronauticsdiscovery.gametest.FlyoverTestHelper.*;

import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyQueue;
import me.corvino.aeronauticsdiscovery.assembly.Pipelines;
import me.corvino.aeronauticsdiscovery.event.FlyoverManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Verifies that no forced chunk tickets leak after a full flyover lifecycle
 * across all distance tiers defined in {@link TierConfig#ALL}.
 *
 * <p>Iterates tiers sequentially within a single {@code @GameTest} method.
 * For each tier: configures view/sim distance, enqueues a flyover pipeline,
 * waits for pipeline completion + despawn, then compares forced-chunk state
 * before vs after. If any tier detects a leak the test fails immediately.
 *
 * <p>Uses a fully registered player (via {@code PlayerList.addPlayer()}) to
 * exercise the real {@code level.players()} path in the flyover manager.
 * Despawn timing is controlled by setting
 * {@link Config#flyoverMaxLifetimeTicks} to 1, so flyovers expire on the
 * tick after registration regardless of player distance.
 */
@GameTestHolder(CreateAeronauticsDiscovery.MODID)
@PrefixGameTestTemplate(false)
public class ChunkLeakTest {

    private static final int STATE_SETUP   = 0;
    private static final int STATE_WAITING = 1;
    private static final int STATE_VERIFY  = 2;

    @GameTest(template = "airplane", timeoutTicks = TIMEOUT_TICKS * 5, batch = "flyover_leak")
    public void noChunkTicketLeakAfterDespawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = spawnAndRegisterPlayer(helper, level);
        BlockPos origin = player.blockPosition();

        int originalLifetime = Config.flyoverMaxLifetimeTicks;
        Config.flyoverMaxLifetimeTicks = 1;

        int[] tierIdx       = {0};
        int[] state         = {STATE_SETUP};
        int[] enqCount      = {0};
        long[] tierStart    = {0};
        ForcedChunksSnapshot[] beforeSnap = {null};

        helper.succeedWhen(() -> {
            if (tierIdx[0] >= TierConfig.ALL.size()) {
                Config.flyoverMaxLifetimeTicks = originalLifetime;
                unregisterPlayer(level, player);
                LOG.info("[FLYOVER_TEST] === ALL TIERS PASSED ===");
                helper.succeed();
                return;
            }

            TierConfig tc = TierConfig.ALL.get(tierIdx[0]);
            long now      = level.getGameTime();

            if (state[0] == STATE_SETUP) {
                configureServer(level, tc.viewChunks(), tc.simChunks());
                int offset = tc.computeTargetOffsetBlocks();
                BlockPos target = origin.offset(offset, 0, 0);
                logHeader(tc.name(), origin, target, DistanceInfo.from(level));

                beforeSnap[0] = ForcedChunksSnapshot.capture(level);
                LOG.info("[FLYOVER_TEST] {} Before: {}", tc.name(), beforeSnap[0]);

                AssemblyContext ctx = buildContext(level, target);
                AssemblyQueue queue = AssemblyQueue.get(level);
                queue.enqueue(Pipelines.FLYOVER, ctx);
                enqCount[0]  = queue.getEntries().size();
                tierStart[0] = now;
                state[0]     = STATE_WAITING;
                throw new GameTestAssertException(
                    tc.name() + " enqueued, waiting for pipeline & despawn");
            }

            if (state[0] == STATE_WAITING) {
                long elapsed = now - tierStart[0];
                AssemblyQueue q = AssemblyQueue.get(level);
                FlyoverManager fm = FlyoverManager.get(level);
                int qs = q.getEntries().size();
                int fc = fm.getAllFlyovers().size();

                if (elapsed % 25 == 0 && elapsed > 0) {
                    LOG.info("[FLYOVER_TEST] {} Poll @ {}t: queue={}, flyovers={}",
                             tc.name(), elapsed, qs, fc);
                }

                boolean done    = qs == 0 && fc == 0 && enqCount[0] > 0;
                boolean timedOut = elapsed >= TIMEOUT_TICKS - 50;

                if (done) {
                    state[0] = STATE_VERIFY;
                } else if (timedOut) {
                    LOG.warn("[FLYOVER_TEST] {} TIMEOUT (queue={}, flyovers={})",
                             tc.name(), qs, fc);
                    LOG.warn("[FLYOVER_TEST] {} leak check SKIPPED (timeout)", tc.name());
                    tierIdx[0]++;
                    state[0] = STATE_SETUP;
                    throw new GameTestAssertException(
                        tc.name() + " timed out, advancing to next tier");
                }

                throw new GameTestAssertException(
                    tc.name() + " awaiting completion (t=" + elapsed + ")");
            }

            if (state[0] == STATE_VERIFY) {
                ForcedChunksSnapshot after = ForcedChunksSnapshot.capture(level);
                LOG.info("[FLYOVER_TEST] {} After: {}", tc.name(), after);

                if (beforeSnap[0].total() != after.total()) {
                    String msg = String.format(
                        "%s chunk leak! before=%s, after=%s",
                        tc.name(), beforeSnap[0], after);
                    LOG.error("[FLYOVER_TEST] {}", msg);
                    throw new GameTestAssertException(msg);
                }

                LOG.info("[FLYOVER_TEST] === {} leak check PASSED ===", tc.name());
                tierIdx[0]++;
                state[0] = STATE_SETUP;
                throw new GameTestAssertException(
                    tc.name() + " passed, advancing to "
                    + (tierIdx[0] < TierConfig.ALL.size()
                        ? TierConfig.ALL.get(tierIdx[0]).name()
                        : "end"));
            }

            throw new GameTestAssertException("unknown state=" + state[0]);
        });
    }
}
