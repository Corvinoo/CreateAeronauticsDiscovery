package me.corvino.aeronauticsdiscovery.pin.trigger;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinNetwork;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerProximityTrigger {
    private PlayerProximityTrigger() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        double radius = Config.playerProximityRadius;
        double radiusSq = radius * radius;

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative()) continue;

            Vec3 center = player.position();
            Map<UUID, PinEntity> candidates = new LinkedHashMap<>();

            // world bound pins
            AABB box = new AABB(
                    center.x - radius, center.y - radius, center.z - radius,
                    center.x + radius, center.y + radius, center.z + radius);
            for (PinEntity pin : level.getEntitiesOfClass(PinEntity.class, box)) {
                candidates.putIfAbsent(pin.getUUID(), pin);
            }

            // sublevel bound pins
            for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(box))) {
                for (PinEntity pin : level.getEntitiesOfClass(PinEntity.class,
                        subLevel.boundingBox().toMojang())) {
                    candidates.putIfAbsent(pin.getUUID(), pin);
                }
            }

            for (PinEntity pin : candidates.values()) {
                if (!pin.isAlive() || !pin.isBound()) continue;
                if (!pin.getTriggerMask().accepts(PinTrigger.Kind.PLAYER_PROXIMITY)) continue;

                double distSq = Sable.HELPER.distanceSquaredWithSubLevels(
                        level, pin.position(), center.x, center.y, center.z);
                if (distSq <= radiusSq) {
                    PinNetwork.triggerDirect(pin, new PinTrigger(PinTrigger.Kind.PLAYER_PROXIMITY, center));
                }
            }
        }
    }
}