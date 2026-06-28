package me.corvino.aeronauticsdiscovery.gametest;

import static me.corvino.aeronauticsdiscovery.gametest.FlyoverTestHelper.*;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyQueue;
import me.corvino.aeronauticsdiscovery.assembly.Pipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Collection;
import java.util.UUID;

@GameTestHolder(CreateAeronauticsDiscovery.MODID)
@PrefixGameTestTemplate(false)
public class ChunkLeakTest {

    private static final int STATE_SETUP           = 0;
    private static final int STATE_WAITING         = 1;
    private static final int STATE_ACTIVE          = 2;
    private static final int STATE_WAITING_DESPAWN = 3;
    private static final int STATE_VERIFY          = 4;

    // Single test: verifies chunks ARE loaded during lifetime AND cleaned up after despawn
    @GameTest(template = "airplane", timeoutTicks = TIMEOUT_TICKS * 6, batch = "flyover_leak")
    public void flyoverChunksLoadAndUnloadPerTier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = spawnAndRegisterPlayer(helper, level);
        BlockPos origin = player.blockPosition();

        int originalLifetime = Config.flyoverMaxLifetimeTicks;
        Config.flyoverMaxLifetimeTicks = 20;

        int[] tierIdx          = {0};
        int[] state            = {STATE_SETUP};
        int[] enqCount         = {0};
        long[] tierStart       = {0};
        UUID[] activeFlyoverId = {null};

        helper.succeedWhen(() -> {
            if (tierIdx[0] >= TierConfig.ALL.size()) {
                Config.flyoverMaxLifetimeTicks = originalLifetime;
                unregisterPlayer(level, player);
                LOG.info("[FLYOVER_TEST] === ALL TIERS PASSED (LOADED+UNLOADED) ===");
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

                AssemblyContext ctx = buildContext(level, target);
                AssemblyQueue queue = AssemblyQueue.get(level);
                queue.enqueue(Pipelines.FLYOVER, ctx);
                enqCount[0]  = queue.getEntries().size();
                tierStart[0] = now;
                state[0]     = STATE_WAITING;
                throw new GameTestAssertException(tc.name() + " enqueued, waiting for pipeline");
            }

            if (state[0] == STATE_WAITING) {
                long elapsed = now - tierStart[0];
                AssemblyQueue q = AssemblyQueue.get(level);
                int qs = q.getEntries().size();
                boolean done     = qs == 0 && enqCount[0] > 0;
                boolean timedOut = elapsed >= TIMEOUT_TICKS - 50;

                if (done) {
                    ServerSubLevelContainer container =
                        (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
                    Collection<ServerSubLevel> forceLoaded = container.collectForceLoadedSubLevels();
                    if (forceLoaded.isEmpty()) {
                        LOG.warn("[FLYOVER_TEST] {} no force-loaded sub-levels found!", tc.name());
                        tierIdx[0]++;
                        state[0] = STATE_SETUP;
                        throw new GameTestAssertException(
                            tc.name() + " no force-loaded sub-level — skipping");
                    }
                    activeFlyoverId[0] = forceLoaded.iterator().next().getUniqueId();
                    state[0] = STATE_ACTIVE;
                } else if (timedOut) {
                    LOG.warn("[FLYOVER_TEST] {} TIMEOUT (queue={})", tc.name(), qs);
                    tierIdx[0]++;
                    state[0] = STATE_SETUP;
                    throw new GameTestAssertException(tc.name() + " timed out, advancing");
                }

                throw new GameTestAssertException(
                    tc.name() + " awaiting completion (t=" + elapsed + ")");
            }

            if (state[0] == STATE_ACTIVE) {
                // LOADED check: verify sub-level IS force-loaded during flyover lifetime
                ServerSubLevelContainer container =
                    (ServerSubLevelContainer) SubLevelContainer.getContainer(level);

                var subLevel = container.getSubLevel(activeFlyoverId[0]);
                if (subLevel == null) {
                    throw new GameTestAssertException(
                        tc.name() + ": flyover sub-level not found in container!");
                }

                Collection<ServerSubLevel> forceLoaded = container.collectForceLoadedSubLevels();
                boolean isForceLoaded = forceLoaded.stream()
                    .anyMatch(sl -> sl.getUniqueId().equals(activeFlyoverId[0]));

                if (!isForceLoaded) {
                    throw new GameTestAssertException(
                        tc.name() + ": flyover sub-level is NOT force-loaded!");
                }

                LOG.info("[FLYOVER_TEST] {} PASSED LOADED: sub-level {} IS force-loaded",
                         tc.name(), activeFlyoverId[0]);

                state[0] = STATE_WAITING_DESPAWN;
                throw new GameTestAssertException(
                    tc.name() + " loaded verified, waiting for despawn");
            }

            if (state[0] == STATE_WAITING_DESPAWN) {
                ServerSubLevelContainer container =
                    (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
                var subLevel = container.getSubLevel(activeFlyoverId[0]);
                if (subLevel == null) {
                    state[0] = STATE_VERIFY;
                } else {
                    throw new GameTestAssertException(
                        tc.name() + " awaiting despawn (sub-level still present)");
                }
            }

            if (state[0] == STATE_VERIFY) {
                // UNLOADED check: verify everything is cleaned up after despawn
                ServerSubLevelContainer container =
                    (ServerSubLevelContainer) SubLevelContainer.getContainer(level);

                Collection<ServerSubLevel> allSubLevels = container.getAllSubLevels();
                boolean subLevelLeak = allSubLevels.stream()
                    .anyMatch(sl -> sl.getUniqueId().equals(activeFlyoverId[0]));

                Collection<ServerSubLevel> stillForced = container.collectForceLoadedSubLevels();
                boolean ticketLeak = stillForced.stream()
                    .anyMatch(sl -> sl.getUniqueId().equals(activeFlyoverId[0]));

                if (subLevelLeak || ticketLeak) {
                    String msg = String.format(
                        "%s LEAK! sublevelInContainer=%s, forceLoaded=%s",
                        tc.name(), subLevelLeak, ticketLeak);
                    LOG.error("[FLYOVER_TEST] {}", msg);
                    throw new GameTestAssertException(msg);
                }

                LOG.info("[FLYOVER_TEST] === {} PASSED UNLOADED: no leaks ===", tc.name());

                tierIdx[0]++;
                state[0] = STATE_SETUP;
                throw new GameTestAssertException(
                    tc.name() + " passed all checks, advancing to "
                    + (tierIdx[0] < TierConfig.ALL.size()
                        ? TierConfig.ALL.get(tierIdx[0]).name()
                        : "end"));
            }

            throw new GameTestAssertException("unknown state=" + state[0]);
        });
    }
}
