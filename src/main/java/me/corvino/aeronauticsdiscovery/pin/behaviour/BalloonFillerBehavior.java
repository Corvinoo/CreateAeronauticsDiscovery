package me.corvino.aeronauticsdiscovery.pin.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.Balloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.ServerBalloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.BalloonMap;
import dev.eriksonn.aeronautics.content.blocks.hot_air.lifting_gas.LiftingGasHolder;
import dev.eriksonn.aeronautics.index.AeroTags;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
import me.corvino.aeronauticsdiscovery.scheduler.TaskScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;

public record BalloonFillerBehavior(double fillAmount) implements PinBehavior<BalloonFillerBehavior> {

    public static final PinBehaviorType<BalloonFillerBehavior> TYPE = PinBehaviorTypes.<BalloonFillerBehavior>register(
            "balloon_filler",
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.DOUBLE.fieldOf("fill_amount").forGetter(BalloonFillerBehavior::fillAmount)
            ).apply(instance, BalloonFillerBehavior::new)),
            List.of(
                    new ConfigField("fill_amount", "Fill Amount", ConfigField.FieldType.DOUBLE, 1.0)
            ),
            0x8000FFAA
    );

    private static final int MAX_RAYCAST_RANGE = 80;
    private static final int RETRY_INTERVAL_TICKS = 2;
    private static final int TIMEOUT_TICKS = 1200;

    @Override
    public PinBehaviorType<BalloonFillerBehavior> type() {
        return TYPE;
    }

    @Override
    public void onTrigger(PinEntity self, PinTrigger trigger) {
        if (!(self.level() instanceof ServerLevel serverLevel)) return;

        BlockPos interiorPos = findBalloonInterior(serverLevel, self.blockPosition());
        if (interiorPos == null) return;

        tryFill(serverLevel, interiorPos, TIMEOUT_TICKS);
    }

    private void tryFill(ServerLevel serverLevel, BlockPos interiorPos, int remainingTicks) {
        Balloon balloon = BalloonMap.MAP.get(serverLevel).getBalloon(interiorPos);
        if (!(balloon instanceof ServerBalloon serverBalloon)) {
            retry(serverLevel, interiorPos, remainingTicks);
            return;
        }
        if (serverBalloon.isAssembling()) {
            retry(serverLevel, interiorPos, remainingTicks);
            return;
        }
        if (serverBalloon.getHeaters().isEmpty()) {
            retry(serverLevel, interiorPos, remainingTicks);
            return;
        }

        List<LiftingGasHolder> holders = serverBalloon.getLiftingGasHolders();
        double capacity = serverBalloon.getCapacity();
        double fillTarget = capacity * this.fillAmount;
        double perType = fillTarget / holders.size();
        for (LiftingGasHolder holder : holders) {
            holder.data().amount = perType;
            holder.data().target = perType;
        }
    }

    private void retry(ServerLevel serverLevel, BlockPos interiorPos, int remainingTicks) {
        if (remainingTicks <= 0) {
            CreateAeronauticsDiscovery.LOGGER.warn("[BalloonFiller] Timed out after {} ticks at {}", TIMEOUT_TICKS, interiorPos);
            return;
        }
        TaskScheduler.getInstance().runSyncLater(
                () -> tryFill(serverLevel, interiorPos, remainingTicks - RETRY_INTERVAL_TICKS),
                RETRY_INTERVAL_TICKS);
    }

    private static BlockPos findBalloonInterior(Level level, BlockPos pinPos) {
        Vec3 start = Vec3.atCenterOf(pinPos.above());
        Vec3 end = Vec3.atCenterOf(pinPos.above(MAX_RAYCAST_RANGE));

        BlockHitResult clip = level.clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()
        ));

        if (clip.getType() == HitResult.Type.MISS) return null;

        BlockPos hitPos = clip.getBlockPos();
        if (!level.getBlockState(hitPos).is(AeroTags.BlockTags.AIRTIGHT)) return null;

        return hitPos.relative(clip.getDirection());
    }
}
