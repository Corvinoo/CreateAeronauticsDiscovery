package me.corvino.aeronauticsdiscovery.marker.trigger;

import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import me.corvino.aeronauticsdiscovery.marker.MarkerNetwork;
import me.corvino.aeronauticsdiscovery.marker.MarkerTrigger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

public final class ProjectileImpactTrigger {
    private ProjectileImpactTrigger() {}

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult().getType() != HitResult.Type.ENTITY) return;

        var entityHit = (EntityHitResult) event.getRayTraceResult();
        if (!(entityHit.getEntity() instanceof MarkerEntity marker)) return;
        if (!(marker.level() instanceof ServerLevel serverLevel)) return;
        if (!marker.isAlive() || !marker.isBound()) return;

        MarkerTrigger trigger = new MarkerTrigger(MarkerTrigger.Kind.PROJECTILE, marker.position());
        MarkerNetwork.triggerDirect(marker, trigger);
        event.setCanceled(true);
    }
}
