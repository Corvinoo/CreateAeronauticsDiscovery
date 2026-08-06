package me.corvino.aeronauticsdiscovery.mixin.hook;

import dev.ryanhcode.sable.Sable;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.AUTOPILOT;

/**
 * Fix for sub-level rider loss on world reload.
 */
@Mixin(Entity.class)
public abstract class SubLevelRiderLoadKickGuardMixin {

    @Unique
    private static final double PLOT_GRID_SCALE = 1e6;

    @Inject(method = "setPos(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void aeronauticsdiscovery$guardWorldSpaceLoadKick(Vec3 pos, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Level level = self.level();
        if (level == null || level.isClientSide) return;

        boolean atLoad = self.tickCount == 0 || isEntityDeserializerThread();
        if (!atLoad) return;

        Vec3 old = self.position();
        boolean hugeTarget = isPlotGridScale(pos);
        boolean oldContained = Sable.HELPER.getContaining(level, old) != null;

        if (hugeTarget && !oldContained && !old.equals(Vec3.ZERO)) {
            ModLog.info(AUTOPILOT,
                    "PILOT-GUARD blocked load-time sub-level rider kick: {} stayed at {} (blocked {} -> {})",
                    self.getType().getDescriptionId(), old, old, pos);
            ci.cancel();
        }
    }

    @Unique
    private static boolean isPlotGridScale(Vec3 v) {
        return v.x() > PLOT_GRID_SCALE || v.x() < -PLOT_GRID_SCALE
                || v.y() > PLOT_GRID_SCALE || v.y() < -PLOT_GRID_SCALE
                || v.z() > PLOT_GRID_SCALE || v.z() < -PLOT_GRID_SCALE;
    }

    @Unique
    private static boolean isEntityDeserializerThread() {
        String name = Thread.currentThread().getName();
        return name != null && name.contains("entity-deserializer");
    }
}
