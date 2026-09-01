package me.corvino.aeronauticsdiscovery.seat;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.entities.SoaringTrader;
import me.corvino.aeronauticsdiscovery.util.ModLog;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.SEAT;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static me.corvino.aeronauticsdiscovery.entities.EntityRegistry.SOARING_TRADER;
import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;

public final class SeatPopulator {
    private SeatPopulator() {}

    public static void spawnTraders(SubLevel subLevel) {
        Level level = subLevel.getLevel();
        for (BlockPos seatPos : findSeatPositions(subLevel)) {
            var projected = Sable.HELPER.projectOutOfSubLevel(level,
                    new Vec3(seatPos.getX(), seatPos.getY(), seatPos.getZ()));

            SoaringTrader trader = SOARING_TRADER.get().create(level);
            if (trader == null) {
                ModLog.warn(SEAT, "Failed to create trader at {}", seatPos);
                continue;
            }

            trader.setPos(projected.x() + 0.5, projected.y(), projected.z() + 0.5);
            trader.getPersistentData().putUUID(SUBLEVEL_ID_TAG, subLevel.getUniqueId()); 
trader.setPersistenceRequired(); //fixes random despawn

            if (!level.addFreshEntity(trader)) {
                ModLog.warn(SEAT, "addFreshEntity failed at {}", seatPos);
            }
        }
    }

    public static void sitTraders(SubLevel subLevel) {
        Level level = subLevel.getLevel();
        AABB bounds = subLevel.getPlot().getBoundingBox().toAABB();


        level.getEntitiesOfClass(SoaringTrader.class, bounds.inflate(1), trader ->
                subLevel.getUniqueId().equals(trader.getPersistentData().getUUID(SUBLEVEL_ID_TAG))
                        && !trader.isPassenger()
        ).forEach(trader -> {
            findSeatPositions(subLevel).stream()
                    .min(Comparator.comparingDouble(pos -> trader.distanceToSqr(Vec3.atCenterOf(pos))))
                    .ifPresent(seatPos -> {
                        if (!trader.isAlive()) return;
                        SeatBlock.sitDown(level, seatPos, trader);
                        ModLog.debug(SEAT, "Trader sit at {}", seatPos);
                    });
        });
    }

    private static List<BlockPos> findSeatPositions(SubLevel subLevel) {
        Level level = subLevel.getLevel();
        AABB bounds = subLevel.getPlot().getBoundingBox().toAABB();
        BoundingBox box = BoundingBox.fromCorners(
                BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ)
        );

        List<BlockPos> seats = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(pos).getBlock() instanceof SeatBlock)
                seats.add(pos.immutable());
        }
        return seats;
    }
}