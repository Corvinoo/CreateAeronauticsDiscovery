package me.corvino.aeronauticsdiscovery.seat;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.entities.SoaringTrader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

import static me.corvino.aeronauticsdiscovery.entities.EntityRegistry.SOARING_TRADER;

public final class SeatPopulator {
    private SeatPopulator() {}

    public static void populateSeats(SubLevel subLevel) {
        Level level = subLevel.getLevel();

        AABB bounds = subLevel.getPlot().getBoundingBox().toAABB();
        BoundingBox box = BoundingBox.fromCorners(
                BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ)
        );

        for (BlockPos pos : BlockPos.betweenClosed(
                box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ())) {
            BlockPos seatPos = pos.immutable();
            if (!(level.getBlockState(seatPos).getBlock() instanceof SeatBlock)) continue;

            var positionProjectedOut = Sable.HELPER.projectOutOfSubLevel(level, new Vec3(seatPos.getX(), seatPos.getY(), seatPos.getZ()));

            SoaringTrader trader = SOARING_TRADER.get().create(level);
            if (trader == null) continue;

            trader.setPos(positionProjectedOut.x() + 0.5, positionProjectedOut.y(), positionProjectedOut.z() + 0.5);
            level.addFreshEntity(trader);
            SeatBlock.sitDown(level, seatPos, trader);
        }
    }
}
