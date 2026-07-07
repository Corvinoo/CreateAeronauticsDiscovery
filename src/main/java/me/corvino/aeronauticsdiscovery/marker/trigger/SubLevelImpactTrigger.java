package me.corvino.aeronauticsdiscovery.marker.trigger;

import me.corvino.aeronauticsdiscovery.event.SubLevelImpactEvent;
import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import me.corvino.aeronauticsdiscovery.marker.MarkerNetwork;
import me.corvino.aeronauticsdiscovery.marker.MarkerTrigger;
import me.corvino.aeronauticsdiscovery.marker.behaviour.ChainExplosiveBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;

public final class SubLevelImpactTrigger {
    private SubLevelImpactTrigger() {}

    public static void onSubLevelImpact(SubLevelImpactEvent event) {
        ServerLevel level = event.getLevel();
        UUID subLevelId = event.getSubLevel().getUniqueId();
        Vec3 impactPos = new Vec3(
                event.getImpactPosition().x,
                event.getImpactPosition().y,
                event.getImpactPosition().z
        );

        MarkerEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (MarkerEntity marker : level.getEntitiesOfClass(MarkerEntity.class,
                event.getSubLevel().boundingBox().toMojang())) {
            if (!marker.isAlive() || !marker.isBound()) continue;
            if (!subLevelId.equals(marker.getPersistentData().getUUID(SUBLEVEL_ID_TAG))) continue;
            if (!ChainExplosiveBehavior.TYPE.id().equals(marker.getBehaviorId())) continue;

            double distSq = marker.position().distanceToSqr(impactPos);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = marker;
            }
        }

        if (closest != null) {
            MarkerNetwork.triggerDirect(closest,
                    new MarkerTrigger(MarkerTrigger.Kind.EXTERNAL_FORCE, impactPos));
        }
    }
}
