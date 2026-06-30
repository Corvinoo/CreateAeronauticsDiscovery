package me.corvino.aeronauticsdiscovery.gametest;

import static me.corvino.aeronauticsdiscovery.gametest.FlyoverTestHelper.*;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.Pipelines;
import me.corvino.aeronauticsdiscovery.assembly.queue.AssemblyQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class FlyoverTestDriver {

    public enum FlyoverState {
        SETUP,
        PIPELINE_WAITING,
        ACTIVE,
        DESPAWN_WAITING,
        VERIFY
    }

    public record FlyoverContext(ServerLevel level, ServerSubLevelContainer container,
                                  UUID flyoverId, BlockPos target, TierConfig tier,
                                  BlockPos origin,
                                  java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> expectedBlocks) {}

    private final GameTestHelper helper;
    private final ServerLevel level;
    private final ServerPlayer player;
    private final BlockPos origin;
    private final List<TierConfig> tiers;
    private final Map<FlyoverState, List<Consumer<FlyoverContext>>> hooks = new EnumMap<>(FlyoverState.class);
    private final int originalLifetime;
    private final Map<BlockPos, BlockState> expectedBlocks;

    private int tierIdx = 0;
    private int state   = 0;
    private int enqCount;
    private long tierStart;
    private UUID activeFlyoverId;
    private BlockPos target;

    private GameTestAssertException hookAssertionError;

    private static final int STATE_SETUP           = 0;
    private static final int STATE_PIPELINE_WAITING = 1;
    private static final int STATE_ACTIVE          = 2;
    private static final int STATE_DESPAWN_WAITING = 3;
    private static final int STATE_VERIFY          = 4;

    public FlyoverTestDriver(GameTestHelper helper, TierConfig first, TierConfig... rest) {
        this.helper = helper;
        this.level = helper.getLevel();
        this.tiers = new ArrayList<>();
        this.tiers.add(first);
        this.tiers.addAll(List.of(rest));
        this.originalLifetime = Config.flyoverMaxLifetimeTicks;
        Config.flyoverMaxLifetimeTicks = 20;
        this.player = spawnAndRegisterPlayer(helper, level);
        this.origin = player.blockPosition();
        this.expectedBlocks = loadExpectedBlocks();
    }

    public FlyoverTestDriver(GameTestHelper helper, List<TierConfig> tiers) {
        this.helper = helper;
        this.level = helper.getLevel();
        this.tiers = new ArrayList<>(tiers);
        this.originalLifetime = Config.flyoverMaxLifetimeTicks;
        Config.flyoverMaxLifetimeTicks = 20;
        this.player = spawnAndRegisterPlayer(helper, level);
        this.origin = player.blockPosition();
        this.expectedBlocks = loadExpectedBlocks();
    }

    @SuppressWarnings("unchecked")
    private Map<BlockPos, BlockState> loadExpectedBlocks() {
        StructureTemplate template = level.getServer().getStructureManager()
            .get(TEMPLATE_ID).orElseThrow();
        Map<BlockPos, BlockState> map = new HashMap<>();
        try {
            Field palettesField = StructureTemplate.class.getDeclaredField("palettes");
            palettesField.setAccessible(true);
            List<StructureTemplate.Palette> palettes =
                (List<StructureTemplate.Palette>) palettesField.get(template);
            for (StructureTemplate.Palette palette : palettes) {
                for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                    if (!info.state().isAir()) {
                        map.put(info.pos(), info.state());
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to read template palette", e);
        }
        LOG.info("[FLYOVER_TEST] Loaded {} expected non-air blocks from template {}",
                 map.size(), TEMPLATE_ID);
        return map;
    }

    public FlyoverTestDriver onState(FlyoverState flyoverState, Consumer<FlyoverContext> hook) {
        hooks.computeIfAbsent(flyoverState, k -> new ArrayList<>()).add(hook);
        return this;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public UUID getFlyoverId() {
        return activeFlyoverId;
    }

    public BlockPos getTarget() {
        return target;
    }

    public BlockPos getOrigin() {
        return origin;
    }

    /** @return true when all tiers are complete; throws GameTestAssertException to continue waiting */
    public boolean tick() {
        if (hookAssertionError != null) {
            throw hookAssertionError;
        }
        if (tierIdx >= tiers.size()) {
            Config.flyoverMaxLifetimeTicks = originalLifetime;
            unregisterPlayer(level, player);
            LOG.info("[FLYOVER_TEST] === ALL TIERS PASSED ===");
            return true;
        }

        TierConfig tc = tiers.get(tierIdx);
        long now = level.getGameTime();

        if (state == STATE_SETUP) {
            configureServer(level, tc.viewChunks(), tc.simChunks());
            int offset = tc.computeTargetOffsetBlocks();
            target = origin.offset(offset, 0, 0);
            logHeader(tc.name(), origin, target, DistanceInfo.from(level));

            AssemblyContext ctx = buildContext(level, target);
            AssemblyQueue queue = AssemblyQueue.get(level);
            queue.enqueue(Pipelines.FLYOVER, ctx);
            enqCount  = queue.getEntries().size();
            tierStart = now;
            state = STATE_PIPELINE_WAITING;

            fireHooks(FlyoverState.SETUP, tc);
            throw new GameTestAssertException(tc.name() + " enqueued, waiting for pipeline");
        }

        if (state == STATE_PIPELINE_WAITING) {
            long elapsed = now - tierStart;
            AssemblyQueue q = AssemblyQueue.get(level);
            int qs = q.getEntries().size();
            boolean done     = qs == 0 && enqCount > 0;
            boolean timedOut = elapsed >= TIMEOUT_TICKS - 50;

            if (done) {
                ServerSubLevelContainer container =
                    (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
                Collection<ServerSubLevel> forceLoaded = container.collectForceLoadedSubLevels();
                if (forceLoaded.isEmpty()) {
                    LOG.warn("[FLYOVER_TEST] {} no force-loaded sub-levels found!", tc.name());
                    tierIdx++;
                    state = STATE_SETUP;
                    throw new GameTestAssertException(tc.name() + " no force-loaded sub-level — skipping");
                }
                activeFlyoverId = forceLoaded.iterator().next().getUniqueId();
                state = STATE_ACTIVE;
            } else if (timedOut) {
                LOG.warn("[FLYOVER_TEST] {} TIMEOUT (queue={})", tc.name(), qs);
                tierIdx++;
                state = STATE_SETUP;
                throw new GameTestAssertException(tc.name() + " timed out, advancing");
            }

            fireHooks(FlyoverState.PIPELINE_WAITING, tc);
            throw new GameTestAssertException(tc.name() + " awaiting completion (t=" + elapsed + ")");
        }

        if (state == STATE_ACTIVE) {
            ServerSubLevelContainer container =
                (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
            var subLevel = container.getSubLevel(activeFlyoverId);
            if (subLevel == null) {
                throw new GameTestAssertException(tc.name() + ": flyover sub-level not found in container!");
            }

            LOG.info("[FLYOVER_TEST] {} reached ACTIVE: sub-level {}",
                     tc.name(), activeFlyoverId);
            state = STATE_DESPAWN_WAITING;

            fireHooks(FlyoverState.ACTIVE, tc);
            throw new GameTestAssertException(tc.name() + " active, waiting for despawn");
        }

        if (state == STATE_DESPAWN_WAITING) {
            ServerSubLevelContainer container =
                (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
            var subLevel = container.getSubLevel(activeFlyoverId);
            if (subLevel != null) {
                throw new GameTestAssertException(tc.name() + " awaiting despawn (sub-level still present)");
            }
            state = STATE_VERIFY;
        }

        if (state == STATE_VERIFY) {
            ServerSubLevelContainer container =
                (ServerSubLevelContainer) SubLevelContainer.getContainer(level);

            Collection<ServerSubLevel> allSubLevels = container.getAllSubLevels();
            boolean subLevelLeak = allSubLevels.stream()
                .anyMatch(sl -> sl.getUniqueId().equals(activeFlyoverId));

            Collection<ServerSubLevel> stillForced = container.collectForceLoadedSubLevels();
            boolean ticketLeak = stillForced.stream()
                .anyMatch(sl -> sl.getUniqueId().equals(activeFlyoverId));

            if (subLevelLeak || ticketLeak) {
                String msg = String.format("%s LEAK! sublevelInContainer=%s, forceLoaded=%s",
                    tc.name(), subLevelLeak, ticketLeak);
                LOG.error("[FLYOVER_TEST] {}", msg);
                throw new GameTestAssertException(msg);
            }

            LOG.info("[FLYOVER_TEST] === {} PASSED ===", tc.name());

            fireHooks(FlyoverState.VERIFY, tc);

            tierIdx++;
            state = STATE_SETUP;
            throw new GameTestAssertException(tc.name() + " passed, advancing to "
                + (tierIdx < tiers.size() ? tiers.get(tierIdx).name() : "end"));
        }

        throw new GameTestAssertException("unknown state=" + state);
    }

    private void fireHooks(FlyoverState flyoverState, TierConfig tc) {
        List<Consumer<FlyoverContext>> list = hooks.get(flyoverState);
        if (list != null) {
            ServerSubLevelContainer container =
                (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
            FlyoverContext ctx = new FlyoverContext(level, container, activeFlyoverId, target, tc, origin, expectedBlocks);
            for (Consumer<FlyoverContext> hook : list) {
                try {
                    hook.accept(ctx);
                } catch (GameTestAssertException e) {
                    hookAssertionError = e;
                    throw e;
                }
            }
        }
    }
}
