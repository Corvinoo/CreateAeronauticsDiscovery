package me.corvino.aeronauticsdiscovery.pin.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

import java.util.List;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.PIN;

public record BurnTimeBehavior(int burnTime, boolean superheated) implements PinBehavior<BurnTimeBehavior> {

    public static final PinBehaviorType<BurnTimeBehavior> TYPE = PinBehaviorTypes.<BurnTimeBehavior>register(
            "burn_time",
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("burn_time").forGetter(BurnTimeBehavior::burnTime),
                    Codec.BOOL.fieldOf("superheated").forGetter(BurnTimeBehavior::superheated)
            ).apply(instance, BurnTimeBehavior::new)),
            List.of(
                    new ConfigField("burn_time", "Burn Time (ticks)", ConfigField.FieldType.INTEGER, 3200),
                    new ConfigField("superheated", "Superheated", ConfigField.FieldType.BOOLEAN, false)
            ),
            0x80FFB040
    );

    @Override
    public PinBehaviorType<BurnTimeBehavior> type() {
        return TYPE;
    }

    @Override
    public void onTrigger(PinEntity self, PinTrigger trigger) {
        if (!(self.level() instanceof ServerLevel serverLevel)) return;

        BlockEntity be = findBlockEntity(serverLevel, self.blockPosition(), self);
        if (be == null) {
            ModLog.warn(PIN, "Burn-time pin at {} found no block entity", self.blockPosition());
            return;
        }

        if (!applyTo(serverLevel, be)) {
            ModLog.warn(PIN,
                    "Burn-time pin at {}: {} has no burn-time API, ignoring",
                    self.blockPosition(), be.getClass().getName());
        }
    }

    private boolean applyTo(ServerLevel level, BlockEntity be) {
        if (be instanceof PortableEngineBlockEntity engine) {
            engine.setCurrentBurnTime(this.burnTime);
            engine.setSuperHeated(this.superheated);
            return true;
        }
        if (be instanceof BlazeBurnerBlockEntity burner) {
            applyToBlazeBurner(level, burner);
            return true;
        }
        return false;
    }

    private void applyToBlazeBurner(ServerLevel level, BlazeBurnerBlockEntity burner) {
        BlazeBurnerBlockEntity.FuelType fuel =
                this.superheated ? BlazeBurnerBlockEntity.FuelType.SPECIAL : BlazeBurnerBlockEntity.FuelType.NORMAL;
        int burnTime = Math.max(1, Math.min(this.burnTime, BlazeBurnerBlockEntity.MAX_HEAT_CAPACITY));

        CompoundTag tag = new CompoundTag();
        tag.putInt("fuelLevel", fuel.ordinal());
        tag.putInt("burnTimeRemaining", burnTime);

        burner.loadCustomOnly(tag, level.registryAccess());
        burner.updateBlockState();
    }

    private static BlockEntity findBlockEntity(ServerLevel level, BlockPos worldPos, PinEntity pin) {
        BlockEntity be = level.getBlockEntity(worldPos);
        if (be != null) return be;

        SubLevel sl = Sable.HELPER.getContaining(level, pin.position());
        if (!(sl instanceof ServerSubLevel ssl)) return null;
        if (!(ssl.getPlot() instanceof ServerLevelPlot plot)) return null;

        Vector3d localPos = ssl.logicalPose().transformPositionInverse(new Vector3d(pin.getX(), pin.getY(), pin.getZ()));
        return plot.getEmbeddedLevelAccessor().getBlockEntity(BlockPos.containing(localPos.x(), localPos.y(), localPos.z()));
    }
}