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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerProximityTrigger {
    private PlayerProximityTrigger() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        double radius = Config.playerProximityRadius;
        double radiusSq = radius * radius;

        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative()) continue;
            players.add(player);
        }
        if (players.isEmpty()) return;

        for (ServerPlayer player : players) {
            Vec3 centre = player.position();
            AABB box = new AABB(
                    centre.x - radius, centre.y - radius, centre.z - radius,
                    centre.x + radius, centre.y + radius, centre.z + radius);

            // Dedupe candidates per player so each pin's distance is computed at most once.
            Map<UUID, PinEntity> candidates = new LinkedHashMap<>();

            // world bound pins
            for (PinEntity pin : level.getEntitiesOfClass(PinEntity.class, box)) {
                addCandidate(candidates, pin);
            }

            // sublevel bound pins
            for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(box))) {
                for (PinEntity pin : level.getEntitiesOfClass(PinEntity.class,
                        subLevel.boundingBox().toMojang())) {
                    addCandidate(candidates, pin);
                }
            }

            for (PinEntity pin : candidates.values()) {
                double distSq = Sable.HELPER.distanceSquaredWithSubLevels(
                        level, pin.position(), centre.x, centre.y, centre.z);
                if (distSq <= radiusSq) {
                    PinNetwork.triggerDirect(pin, new PinTrigger(PinTrigger.Kind.PLAYER_PROXIMITY, centre));
                }
            }
        }
    }

    private static void addCandidate(Map<UUID, PinEntity> candidates, PinEntity pin) {
        if (!pin.isAlive() || !pin.isBound()) return;
        if (!pin.getTriggerMask().accepts(PinTrigger.Kind.PLAYER_PROXIMITY)) return;
        candidates.putIfAbsent(pin.getUUID(), pin);
    }
}