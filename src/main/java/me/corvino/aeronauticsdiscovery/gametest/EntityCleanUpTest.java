package me.corvino.aeronauticsdiscovery.gametest;

import static me.corvino.aeronauticsdiscovery.gametest.FlyoverTestHelper.*;
import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.gametest.FlyoverTestDriver.FlyoverContext;
import me.corvino.aeronauticsdiscovery.gametest.FlyoverTestDriver.FlyoverState;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.util.ModLog;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.FLYOVER_TEST;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(CreateAeronauticsDiscovery.MODID)
@PrefixGameTestTemplate(false)
public class EntityCleanUpTest {

    @GameTest(template = "airplane", timeoutTicks = 3000, batch = "entity_cleanup")
    public void entityCleanup(GameTestHelper helper) {
        var d = new FlyoverTestDriver(helper, TierConfig.ALL);
        d.onState(FlyoverState.ACTIVE, this::assertEntitiesPresent);
        d.onState(FlyoverState.VERIFY, this::assertNoLeakedEntities);
        helper.succeedWhen(() -> { if (d.tick()) helper.succeed(); });
    }

    private void assertEntitiesPresent(FlyoverContext ctx) {
        ServerSubLevel subLevel = (ServerSubLevel) ctx.container().getSubLevel(ctx.flyoverId());
        if (subLevel == null) {
            throw new GameTestAssertException(ctx.tier().name() + ": sub-level not found for entity check");
        }

        UUID subId = subLevel.getUniqueId();
        AABB bb = subLevel.boundingBox().toMojang();

        long pinned = ctx.level().getEntitiesOfClass(PinEntity.class, bb,
                pin -> subId.equals(pin.getPersistentData().getUUID(SUBLEVEL_ID_TAG))).size();

        long tagged = 0;
        for (Entity entity : ctx.level().getEntities((Entity) null, bb)) {
            if (entity == null) continue;
            var data = entity.getPersistentData();
            if (data.hasUUID(SUBLEVEL_ID_TAG) && subId.equals(data.getUUID(SUBLEVEL_ID_TAG))) {
                tagged++;
            }
        }

        ModLog.info(FLYOVER_TEST, "{} ACTIVE: {} PinEntities, {} total tagged entities in sub-level bounds",
                ctx.tier().name(), pinned, tagged);

        if (tagged == 0) {
            throw new GameTestAssertException(
                    ctx.tier().name() + ": no entities tagged with SUBLEVEL_ID_TAG found in active sub-level");
        }
    }

    private void assertNoLeakedEntities(FlyoverContext ctx) {
        String tier = ctx.tier().name();
        UUID flyoverId = ctx.flyoverId();

        AABB searchBox = computeSearchBounds(ctx);
        long leaked = 0;

        for (Entity entity : ctx.level().getEntities((Entity) null, searchBox)) {
            if (entity == null) continue;
            var data = entity.getPersistentData();
            if (!data.hasUUID(SUBLEVEL_ID_TAG)) continue;
            if (!flyoverId.equals(data.getUUID(SUBLEVEL_ID_TAG))) continue;
            leaked++;
            if (leaked <= 3) {
                ModLog.warn(FLYOVER_TEST, "{} leaked entity: {} ({}) at {}",
                        tier, entity, entity.getType(), entity.blockPosition());
            }
        }

        if (leaked > 0) {
            throw new GameTestAssertException(String.format(
                    "%s: %d entity(ies) tagged with flyover UUID %s leaked into parent level",
                    tier, leaked, flyoverId));
        }

        ModLog.info(FLYOVER_TEST, "{} entity cleanup: 0 leaked entities", tier);
    }

    private static AABB computeSearchBounds(FlyoverContext ctx) {
        BlockPos target = ctx.target();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos localPos : ctx.expectedBlocks().keySet()) {
            int wx = target.getX() + localPos.getX();
            int wy = target.getY() + localPos.getY();
            int wz = target.getZ() + localPos.getZ();
            if (wx < minX) minX = wx;
            if (wy < minY) minY = wy;
            if (wz < minZ) minZ = wz;
            if (wx > maxX) maxX = wx;
            if (wy > maxY) maxY = wy;
            if (wz > maxZ) maxZ = wz;
        }

        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }
}
