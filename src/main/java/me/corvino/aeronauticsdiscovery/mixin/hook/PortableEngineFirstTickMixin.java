package me.corvino.aeronauticsdiscovery.mixin.hook;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents a newly placed portable engine from validating an inherited/opposite kinetic source before its
 * rotated facing and generated direction have initialized. The template NBT sanitizer removes persisted
 * runtime state, but Create can attach the BE to an adjacent network before its first tick. Reset once before
 * the normal tick; the engine then creates/reconnects its network from the final placed block state.
 */
@Mixin(PortableEngineBlockEntity.class)
public abstract class PortableEngineFirstTickMixin {

    @Unique
    private boolean aeronauticsdiscovery$kineticsInitialized;

    @Inject(method = "tick", at = @At("HEAD"))
    private void aeronauticsdiscovery$resetKineticsBeforeFirstTick(CallbackInfo ci) {
        if (aeronauticsdiscovery$kineticsInitialized) return;
        aeronauticsdiscovery$kineticsInitialized = true;

        KineticBlockEntity kinetic = (KineticBlockEntity) (Object) this;
        if (kinetic.hasNetwork() || kinetic.hasSource() || kinetic.getSpeed() != 0) {
            kinetic.detachKinetics();
            kinetic.clearKineticInformation();
        }
    }
}
