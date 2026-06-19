package me.corvino.aeronauticsdiscovery.seat;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.entities.SoaringTrader;
import net.minecraft.core.BlockPos;
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

    public static List<BlockPos> findSeatPositions(ServerLevel level, BoundingBox bounds) {
        List<BlockPos> seats = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            BlockPos p = pos.immutable();
            if (level.getBlockState(p).getBlock() instanceof SeatBlock) {
                seats.add(p);
            }
        }
        return seats;
    }

    public static void populateSeats(
            SubLevel subLevel, List<BlockPos> worldSeatPositions, BlockPos offset) {
        if (worldSeatPositions.isEmpty()) return;
        Level level = subLevel.getLevel();
        CreateAeronauticsDiscovery.LOGGER.debug(
                "[SEAT] Mounting {} wandering trader(s) on seats in sublevel {}",
                worldSeatPositions.size(), subLevel.getName());
        for (BlockPos worldPos : worldSeatPositions) {
            BlockPos subLevelPos = worldPos.offset(offset);
            CreateAeronauticsDiscovery.LOGGER.debug(
                    "[SEAT] worldPos={}, offset={}, subLevelPos={}",
                    worldPos, offset, subLevelPos);
            SoaringTrader trader = SOARING_TRADER.get().create(level);
            if (trader != null) {
                trader.setPos(subLevelPos.getX() + 0.5, subLevelPos.getY(), subLevelPos.getZ() + 0.5);
                level.addFreshEntity(trader);
                SeatBlock.sitDown(level, subLevelPos, trader);
                for (SeatEntity seat : level.getEntitiesOfClass(SeatEntity.class, new AABB(subLevelPos))) {
                    if (seat.hasPassenger(trader) && seat instanceof EntityStickExtension stickExt) {
                        stickExt.sable$setPlotPosition(Vec3.atLowerCornerOf(subLevelPos));
                        CreateAeronauticsDiscovery.LOGGER.debug(
                                "[SEAT] SeatEntity now tracking SubLevel at plot position {}", subLevelPos);
                        break;
                    }
                }
                CreateAeronauticsDiscovery.LOGGER.debug(
                        "[SEAT] Wandering trader mounted at sublevel pos {}", subLevelPos);
            }
        }
    }
}
