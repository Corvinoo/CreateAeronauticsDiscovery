package me.corvino.aeronauticsdiscovery.pin.trigger;

import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinNetwork;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

public final class ProjectileImpactTrigger {
    private ProjectileImpactTrigger() {}

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult().getType() != HitResult.Type.ENTITY) return;

        var entityHit = (EntityHitResult) event.getRayTraceResult();
        if (!(entityHit.getEntity() instanceof PinEntity pin)) return;
        if (!(pin.level() instanceof ServerLevel serverLevel)) return;
        if (!pin.isAlive() || !pin.isBound()) return;

        PinTrigger trigger = new PinTrigger(PinTrigger.Kind.PROJECTILE, pin.position());
        PinNetwork.triggerDirect(pin, trigger);
        event.setCanceled(true);
    }
}
