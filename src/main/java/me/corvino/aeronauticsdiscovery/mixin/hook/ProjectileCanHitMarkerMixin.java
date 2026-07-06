package me.corvino.aeronauticsdiscovery.mixin.hook;

import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class ProjectileCanHitMarkerMixin {

    @Inject(method = "canHitEntity", at = @At("RETURN"), cancellable = true)
    private void aeronauticsdiscovery$canHitMarker(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && target instanceof MarkerEntity marker && marker.isAlive()) {
            cir.setReturnValue(true);
        }
    }
}
