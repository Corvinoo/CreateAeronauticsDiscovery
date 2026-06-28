package me.corvino.aeronauticsdiscovery.gametest;

import static me.corvino.aeronauticsdiscovery.gametest.ChunkLeakTest.assertNoLeaks;
import static me.corvino.aeronauticsdiscovery.gametest.FlyoverTestHelper.*;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.gametest.FlyoverTestDriver.FlyoverState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(CreateAeronauticsDiscovery.MODID)
@PrefixGameTestTemplate(false)
public class TemplateIntegrityTest {

    @GameTest(template = "airplane", timeoutTicks = 3000, batch = "template_integrity")
    public void templateBlockIntegrity(GameTestHelper helper) {
        var d = new FlyoverTestDriver(helper, TierConfig.ALL);
        d.onState(FlyoverState.ACTIVE, ctx -> assertSubLevelMatchesTemplate(ctx));
        d.onState(FlyoverState.VERIFY, ctx -> assertNoLeaks(ctx.container(), ctx.flyoverId()));
        helper.succeedWhen(() -> { if (d.tick()) helper.succeed(); });
    }
    
    public static void assertForceLoaded(ServerSubLevelContainer container, UUID flyoverId) {
        var subLevel = container.getSubLevel(flyoverId);
        if (subLevel == null) {
            throw new GameTestAssertException(
                    "flyover " + flyoverId + ": sub-level not found in container");
        }

        Collection<ServerSubLevel> forceLoaded = container.collectForceLoadedSubLevels();
        boolean found = forceLoaded.stream().anyMatch(sl -> sl.getUniqueId().equals(flyoverId));
        if (!found) {
            throw new GameTestAssertException(
                    "flyover " + flyoverId + ": sub-level is NOT force-loaded");
        }
    }

    public static void assertSubLevelMatchesTemplate(FlyoverTestDriver.FlyoverContext ctx) {
        ServerSubLevel subLevel = (ServerSubLevel) ctx.container().getSubLevel(ctx.flyoverId());
        if (subLevel == null) {
            throw new GameTestAssertException("sub-level not found for template matching");
        }
        LevelPlot plot = subLevel.getPlot();

        // 1. Read all non-air blocks from the sub-level via loaded chunks
        Map<BlockPos, BlockState> actualByWorldPos = new HashMap<>();
        for (var holder : plot.getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk == null) continue;
            int baseX = holder.getPos().getMinBlockX();
            int baseZ = holder.getPos().getMinBlockZ();
            for (int sy = 0; sy < chunk.getSectionsCount(); sy++) {
                LevelChunkSection section = chunk.getSection(sy);
                if (section == null || section.hasOnlyAir()) continue;
                int sectionBaseY = chunk.getSectionYFromSectionIndex(sy) << 4;
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (state.isAir()) continue;
                            actualByWorldPos.put(
                                    new BlockPos(baseX + x, sectionBaseY + y, baseZ + z),
                                    state);
                        }
                    }
                }
            }
        }

        // 2. Convert to local sub-level coordinates (subtract plot world origin)
        int plotOriginBlockX = plot.getChunkMin().getMinBlockX();
        int plotOriginBlockZ = plot.getChunkMin().getMinBlockZ();
        Map<BlockPos, BlockState> actualLocal = new HashMap<>();
        for (var entry : actualByWorldPos.entrySet()) {
            BlockPos wp = entry.getKey();
            actualLocal.put(new BlockPos(
                    wp.getX() - plotOriginBlockX,
                    wp.getY(),
                    wp.getZ() - plotOriginBlockZ
            ), entry.getValue());
        }

        // 3. Find min local position across actual blocks
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos p : actualLocal.keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
        }

        // 4. Normalize actual: subtract min so smallest block → (0,0,0)
        Map<BlockPos, BlockState> normalizedActual = new HashMap<>();
        for (var entry : actualLocal.entrySet()) {
            BlockPos p = entry.getKey();
            normalizedActual.put(new BlockPos(
                    p.getX() - minX, p.getY() - minY, p.getZ() - minZ
            ), entry.getValue());
        }

        // 5. Normalize expected template blocks similarly
        Map<BlockPos, BlockState> expected = ctx.expectedBlocks();
        int expMinX = Integer.MAX_VALUE, expMinY = Integer.MAX_VALUE, expMinZ = Integer.MAX_VALUE;
        for (BlockPos p : expected.keySet()) {
            expMinX = Math.min(expMinX, p.getX());
            expMinY = Math.min(expMinY, p.getY());
            expMinZ = Math.min(expMinZ, p.getZ());
        }
        Map<BlockPos, BlockState> normalizedExpected = new HashMap<>();
        for (var entry : expected.entrySet()) {
            BlockPos p = entry.getKey();
            normalizedExpected.put(new BlockPos(
                    p.getX() - expMinX, p.getY() - expMinY, p.getZ() - expMinZ
            ), entry.getValue());
        }

        // 6. Subset check: every expected block must be present with correct state.
        // Extra auto-generated blocks (e.g. simulated:white_symmetric_sail) are allowed.
        int matched = 0;
        int missing = 0;
        int mismatched = 0;
        StringBuilder sb = new StringBuilder();
        for (var entry : normalizedExpected.entrySet()) {
            BlockPos normPos = entry.getKey();
            BlockState expectedState = entry.getValue();
            BlockState actualState = normalizedActual.get(normPos);
            if (actualState == null) {
                sb.append(String.format("\n  Missing at norm (%d,%d,%d) → template (%d,%d,%d): %s",
                        normPos.getX(), normPos.getY(), normPos.getZ(),
                        normPos.getX() + expMinX, normPos.getY() + expMinY, normPos.getZ() + expMinZ,
                        expectedState));
                missing++;
            } else if (!expectedState.equals(actualState)) {
                sb.append(String.format("\n  Mismatch at norm (%d,%d,%d) → template (%d,%d,%d): expected %s, got %s",
                        normPos.getX(), normPos.getY(), normPos.getZ(),
                        normPos.getX() + expMinX, normPos.getY() + expMinY, normPos.getZ() + expMinZ,
                        expectedState, actualState));
                mismatched++;
            } else {
                matched++;
            }
        }

        if (missing > 0 || mismatched > 0) {
            int extra = normalizedActual.size() - normalizedExpected.size();
            throw new GameTestAssertException(String.format(
                    "%s: template integrity FAILED (matched=%d, missing=%d, mismatched=%d, extra_in_sublevel=%d)%s",
                    ctx.tier().name(), matched, missing, mismatched, extra, sb));
        }

        int extra = normalizedActual.size() - normalizedExpected.size();
        FlyoverTestHelper.LOG.info(
                "[FLYOVER_TEST] {} template integrity: {} blocks match ({} template, {} actual, {} auto-generated)",
                ctx.tier().name(), matched, normalizedExpected.size(), normalizedActual.size(), extra);
    }
}
