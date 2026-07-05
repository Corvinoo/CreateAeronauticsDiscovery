package me.corvino.aeronauticsdiscovery.gametest;

import static me.corvino.aeronauticsdiscovery.gametest.FlyoverTestHelper.*;
import static me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager.FLYOVER_ID_TAG;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.*;
import me.corvino.aeronauticsdiscovery.assembly.queue.AssemblyQueue;
import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.*;

@GameTestHolder(CreateAeronauticsDiscovery.MODID)
@PrefixGameTestTemplate(false)
public class MarkerTrackingTest {

    @GameTest(template = "airplane", timeoutTicks = 200, batch = "marker_tracking")
    public void markersAreBoundAfterAssembly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int origLifetime = Config.flyoverMaxLifetimeTicks;
        Config.flyoverMaxLifetimeTicks = 200;

        ServerPlayer player = FlyoverTestHelper.spawnAndRegisterPlayer(helper, level);
        BlockPos origin = player.blockPosition();

        System.out.println("[MARKER_DIAG] origin=" + origin);

        AssemblyContext ctx = AssemblyContext.builder()
                .level(level)
                .anchor(origin)
                .templateId(ResourceLocation.parse("aeronauticsdiscovery:plane"))
                .source(AssemblySource.FLYOVER)
                .rotationTemplate(Rotation.NONE)
                .setYaw(0.0)
                .overrideVelocity(new InitialVelocity(new Vec3(0, 0, 3), Vec3.ZERO, false))
                .maxRetries(MAX_RETRIES)
                .setName("marker_tracking_test")
                .registerFlyover()
                .build();

        AssemblyQueue.get(level).enqueue(Pipelines.FLYOVER, ctx);

        long[] startTick = {-1};

        helper.succeedWhen(() -> {
            if (startTick[0] < 0) startTick[0] = level.getGameTime();
            long elapsed = level.getGameTime() - startTick[0];

            if (elapsed > 150) {
                Config.flyoverMaxLifetimeTicks = origLifetime;
                unregisterPlayer(level, player);
                helper.fail("Timed out at tick " + elapsed);
            }

            ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
            if (container == null || container.getAllSubLevels().isEmpty()) {
                throw new GameTestAssertException("Waiting for sub-level... (" + elapsed + ")");
            }

            ServerSubLevel subLevel = container.getAllSubLevels().stream()
                    .max(Comparator.comparingLong(sl -> {
                        var tag = sl.getUserDataTag();
                        return tag != null && tag.contains("template_id") ? 1L : 0L;
                    }))
                    .orElse(container.getAllSubLevels().iterator().next());
            UUID flyoverId = subLevel.getUniqueId();

            if (elapsed < 30) {
                throw new GameTestAssertException("Settling... (" + elapsed + ")");
            }

            AABB bb = subLevel.boundingBox().toMojang();
            List<MarkerEntity> markers = level.getEntitiesOfClass(MarkerEntity.class, bb,
                    m -> flyoverId.equals(m.getPersistentData().getUUID(FLYOVER_ID_TAG)));

            int bound = 0;
            for (MarkerEntity m : markers) {
                System.out.println("[MARKER_DIAG]   Marker " + m.getBehaviorId()
                        + " pos=" + m.position() + " isBound=" + m.isBound());
                if (m.isBound()) bound++;
            }
            System.out.println("[MARKER_DIAG] Found " + markers.size() + " markers (" + bound + " bound)");

            Config.flyoverMaxLifetimeTicks = origLifetime;
            unregisterPlayer(level, player);

            if (markers.isEmpty()) {
                helper.fail("No markers found for flyover " + flyoverId);
            }
            if (bound == 0) {
                helper.fail("Found " + markers.size() + " markers but none are bound");
            }

            System.out.println("[MARKER_DIAG] PASS: " + bound + "/" + markers.size() + " markers bound");
            helper.succeed();
        });
    }
}
