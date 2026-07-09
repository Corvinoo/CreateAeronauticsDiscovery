package me.corvino.aeronauticsdiscovery.pin.trigger;

import me.corvino.aeronauticsdiscovery.entities.SoaringTrader;
import me.corvino.aeronauticsdiscovery.event.SubLevelImpactEvent;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinNetwork;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
import me.corvino.aeronauticsdiscovery.pin.behaviour.ChainExplosiveBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static me.corvino.aeronauticsdiscovery.Config.traderAngerDuration;
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
        // making soaring trader angry todo: probably move trigger elsewhere
        for (SoaringTrader trader : level.getEntitiesOfClass(SoaringTrader.class,
                event.getSubLevel().boundingBox().toMojang())) {
            var data = trader.getPersistentData();
            if (data.hasUUID(SUBLEVEL_ID_TAG) && subLevelId.equals(data.getUUID(SUBLEVEL_ID_TAG)))
                trader.makeAngry(traderAngerDuration);
        }

        PinEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (PinEntity pin : level.getEntitiesOfClass(PinEntity.class,
                event.getSubLevel().boundingBox().toMojang())) {
            if (!pin.isAlive() || !pin.isBound()) continue;
            var data = pin.getPersistentData();
            if (!data.hasUUID(SUBLEVEL_ID_TAG)) continue;
            if (!subLevelId.equals(data.getUUID(SUBLEVEL_ID_TAG))) continue;
            if (!ChainExplosiveBehavior.TYPE.id().equals(pin.getBehaviorId())) continue;

            double distSq = pin.position().distanceToSqr(impactPos);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = pin;
            }
        }

        if (closest != null) {
            PinNetwork.triggerDirect(closest,
                    new PinTrigger(PinTrigger.Kind.EXTERNAL_FORCE, impactPos));
        }
    }
}
