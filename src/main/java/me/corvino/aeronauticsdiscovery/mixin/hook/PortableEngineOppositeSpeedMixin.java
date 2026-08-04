package me.corvino.aeronauticsdiscovery.mixin.hook;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.GEN;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create normally destroys a generator when an attached source reverses its speed. A prefab portable engine
 * can transiently see that state while its rotated facing/network is initializing. Suppress only the destructive
 * branch and preserve the current source/speed so the portable engine's own direction correction can run.
 */
@Mixin(GeneratingKineticBlockEntity.class)
public abstract class PortableEngineOppositeSpeedMixin {

    @Inject(method = "applyNewSpeed", at = @At("HEAD"), cancellable = true)
    private void aeronauticsdiscovery$recoverOppositePortableEngineSpeed(
            float previousSpeed, float newSpeed, CallbackInfo ci) {
        KineticBlockEntity kinetic = (KineticBlockEntity) (Object) this;
        if (!(kinetic instanceof PortableEngineBlockEntity)
                || !kinetic.hasSource()
                || previousSpeed == 0
                || newSpeed == 0
                || Math.signum(previousSpeed) == Math.signum(newSpeed)
                || Math.abs(previousSpeed) < Math.abs(newSpeed)) {
            return;
        }

        ModLog.warn(GEN,
                "Portable engine recovered from opposite kinetic source at {}: previousSpeed={}, newSpeed={}",
                kinetic.getBlockPos(), previousSpeed, newSpeed);
        ci.cancel();
    }
}
