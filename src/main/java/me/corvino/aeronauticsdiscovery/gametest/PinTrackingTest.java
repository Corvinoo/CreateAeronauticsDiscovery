package me.corvino.aeronauticsdiscovery.gametest;

import static me.corvino.aeronauticsdiscovery.gametest.FlyoverTestHelper.*;
import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.*;
import me.corvino.aeronauticsdiscovery.assembly.queue.AssemblyQueue;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
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
public class PinTrackingTest {

    @GameTest(template = "airplane", timeoutTicks = 200, batch = "pin_tracking")
    public void pinsAreBoundAfterAssembly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int origLifetime = Config.flyoverMaxLifetimeTicks;
        Config.flyoverMaxLifetimeTicks = 200;

        ServerPlayer player = FlyoverTestHelper.spawnAndRegisterPlayer(helper, level);
        BlockPos origin = player.blockPosition();

        System.out.println("[PIN_DIAG] origin=" + origin);

        AssemblyContext ctx = AssemblyContext.builder()
                .level(level)
                .anchor(origin)
                .templateId(ResourceLocation.parse("aeronauticsdiscovery:plane"))
                .source(AssemblySource.FLYOVER)
                .rotationTemplate(Rotation.NONE)
                .setYaw(0.0)
                .overrideVelocity(new InitialVelocity(new Vec3(0, 0, 3), Vec3.ZERO, false))
                .maxRetries(MAX_RETRIES)
                .setName("pin_tracking_test")
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
            List<PinEntity> pins = level.getEntitiesOfClass(PinEntity.class, bb,
                    m -> flyoverId.equals(m.getPersistentData().getUUID(SUBLEVEL_ID_TAG)));

            int bound = 0;
            for (PinEntity m : pins) {
                System.out.println("[PIN_DIAG]   Pin " + m.getBehaviorId()
                        + " pos=" + m.position() + " isBound=" + m.isBound());
                if (m.isBound()) bound++;
            }
            System.out.println("[PIN_DIAG] Found " + pins.size() + " pins (" + bound + " bound)");

            Config.flyoverMaxLifetimeTicks = origLifetime;
            unregisterPlayer(level, player);

            if (pins.isEmpty()) {
                helper.fail("No pins found for flyover " + flyoverId);
            }
            if (bound == 0) {
                helper.fail("Found " + pins.size() + " pins but none are bound");
            }

            System.out.println("[PIN_DIAG] PASS: " + bound + "/" + pins.size() + " pins bound");
            helper.succeed();
        });
    }
}
