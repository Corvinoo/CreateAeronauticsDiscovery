package me.corvino.aeronauticsdiscovery.gametest;

import static me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager.FLYOVER_ID_TAG;
import static me.corvino.aeronauticsdiscovery.gametest.FlyoverTestHelper.*;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.entities.SoaringTrader;
import me.corvino.aeronauticsdiscovery.gametest.FlyoverTestDriver.FlyoverState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(CreateAeronauticsDiscovery.MODID)
@PrefixGameTestTemplate(false)
public class DespawnCleanupTest {

    @GameTest(template = "airplane", timeoutTicks = 3000, batch = "despawn_cleanup")
    public void despawnCleanup(GameTestHelper helper) {
        var d = new FlyoverTestDriver(helper, TierConfig.ALL);
        d.onState(FlyoverState.ACTIVE, this::assertSubLevelHasEntities);
        d.onState(FlyoverState.ACTIVE, TemplateIntegrityTest::assertSubLevelMatchesTemplate);
        d.onState(FlyoverState.VERIFY, ctx -> ChunkLeakTest.assertNoLeaks(ctx.container(), ctx.flyoverId()));
        d.onState(FlyoverState.VERIFY, this::assertParentLevelCleanup);
        helper.succeedWhen(() -> { if (d.tick()) helper.succeed(); });
    }


    private void assertSubLevelHasEntities(FlyoverTestDriver.FlyoverContext ctx) {
        ServerSubLevel subLevel = (ServerSubLevel) ctx.container().getSubLevel(ctx.flyoverId());
        if (subLevel == null) {
            throw new GameTestAssertException("sub-level not found for entity check");
        }

        ServerLevel parent = subLevel.getLevel();
        UUID subId = subLevel.getUniqueId();
        AABB bb = subLevel.boundingBox().toMojang();

        int total = 0;
        int traders = 0;
        // Entities are spawned in the parent level tagged with the sub-level UUID
        // (not inside the sub-level itself), so search parent by bounding box + tag.
        for (Entity entity : parent.getEntities((Entity) null, bb)) {
            if (entity == null) continue;
            var data = entity.getPersistentData();
            if (!data.contains(FLYOVER_ID_TAG)) continue;
            if (!subId.equals(data.getUUID(FLYOVER_ID_TAG))) continue;
            total++;
            if (entity instanceof SoaringTrader) traders++;
        }

        FlyoverTestHelper.LOG.info(
                "[FLYOVER_TEST] {} sub-level entities: {} total, {} SoaringTraders",
                ctx.tier().name(), total, traders);

        if (traders == 0) {
            throw new GameTestAssertException(
                    ctx.tier().name() + ": no SoaringTrader entities in sub-level");
        }
    }

    private void assertParentLevelCleanup(FlyoverTestDriver.FlyoverContext ctx) {
        String tier = ctx.tier().name();

        // 1. No entities tagged with this flyover UUID leaked to parent level
        long leaked = 0;
        for (Entity entity : ctx.level().getAllEntities()) {
            if (entity == null) continue;
            var data = entity.getPersistentData();
            if (!data.contains(FLYOVER_ID_TAG)) continue;
            if (!ctx.flyoverId().equals(data.getUUID(FLYOVER_ID_TAG))) continue;
            leaked++;
            if (leaked <= 3) {
                FlyoverTestHelper.LOG.warn(
                        "[FLYOVER_TEST] {} leaked entity: {} at {}",
                        tier, entity, entity.blockPosition());
            }
        }
        if (leaked > 0) {
            throw new GameTestAssertException(String.format(
                    "%s: %d entity(ies) tagged with flyover UUID leaked into parent level",
                    tier, leaked));
        }

        // 2. Every template block position in the parent level must be air (cleaned up)
        BlockPos target = ctx.target();
        int dirty = 0;
        for (var entry : ctx.expectedBlocks().entrySet()) {
            BlockPos tp = entry.getKey();
            BlockPos worldPos = new BlockPos(
                    target.getX() + tp.getX(),
                    target.getY() + tp.getY(),
                    target.getZ() + tp.getZ()
            );
            if (!ctx.level().getBlockState(worldPos).isAir()) {
                dirty++;
                if (dirty <= 5) {
                    FlyoverTestHelper.LOG.warn(
                            "[FLYOVER_TEST] {} block not cleaned at world {}: expected air, got {}",
                            tier, worldPos, ctx.level().getBlockState(worldPos));
                }
            }
        }

        if (dirty > 0) {
            throw new GameTestAssertException(String.format(
                    "%s: %d/%d template block positions in parent level are not air (cleanup failed)",
                    tier, dirty, ctx.expectedBlocks().size()));
        }

        FlyoverTestHelper.LOG.info(
                "[FLYOVER_TEST] {} cleanup: 0 leaked entities, 0 stray blocks across {} checked positions",
                tier, ctx.expectedBlocks().size());
    }
}
