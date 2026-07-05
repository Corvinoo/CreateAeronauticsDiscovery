package me.corvino.aeronauticsdiscovery.marker;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager.FLYOVER_ID_TAG;

/**
 * Handles "markers aware of other markers" without ever giving one marker a direct reference to another.
 * <p>
 * Direct entity-to-entity references (cached UUIDs, held Entity pointers) go stale across chunk
 * unload/reload and get expensive fast if every marker independently scans the world every tick for
 * neighbours. Instead:
 * <ul>
 *   <li>Membership in a flyover's marker set is never stored explicitly - it's resolved on demand by
 *   querying a world-space region around the trigger for {@link MarkerEntity} instances tagged with that
 *   sub-level's {@link me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager#FLYOVER_ID_TAG},
 *   exactly the same convention {@code SeatPopulator} already uses for traders.</li>
 *   <li>A trigger (explosion, external force, proximity) is posted once via {@link #notifyTrigger}, which
 *   does a single distance pass over that flyover's markers and schedules delayed callbacks for the ones
 *   that should react - rather than each marker polling its surroundings every tick.</li>
 * </ul>
 * Once a marker is bound via {@link MarkerEntity#bindToSubLevel}, Sable's own tracking keeps its
 * {@code position()} correctly updated in real world space every tick (see that method's javadoc), so
 * ordinary world-space distances between markers stay correct through the sub-level's motion/rotation -
 * there's no need to work in plot-local space for this.
 */
public final class MarkerNetwork {
    private MarkerNetwork() {}

    private record ScheduledTask(long targetTick, Runnable task) {}

    private static final Map<UUID, List<ScheduledTask>> PENDING = new HashMap<>();

    /**
     * Posts a trigger to every marker bound to {@code subLevelId}, searched for within
     * {@code searchRadius} world-space blocks of {@code trigger.originWorldPos()} (pass something at least
     * as large as the flyover's own extent plus your max propagation distance). Markers within
     * {@code immediateRadius} react immediately; every other bound marker found still gets notified, but
     * after a delay of {@code distance / propagationSpeedPerTick} ticks - giving a natural "explosion
     * travels outward" chain-reaction feel without any marker needing to know about any other.
     */
    public static void notifyTrigger(ServerLevel level, UUID subLevelId, MarkerTrigger trigger,
                                     double searchRadius, double immediateRadius, double propagationBlocksPerTick,
                                     long currentTick, int maxChainDepth) {
        if (trigger.chainDepth() > maxChainDepth) return;

        for (MarkerEntity marker : resolveBoundMarkers(level, subLevelId, trigger.originWorldPos(), searchRadius)) {
            double distance = marker.position().distanceTo(trigger.originWorldPos());

            if (distance <= immediateRadius) {
                fire(marker, trigger);
            } else if (propagationBlocksPerTick > 0) {
                long delayTicks = (long) Math.ceil(distance / propagationBlocksPerTick);
                schedule(subLevelId, currentTick + delayTicks, () -> fire(marker, trigger.withDepth(trigger.chainDepth() + 1)));
            }
        }
    }


    /** Call once per server tick (e.g. from {@code FlyoverManager#tick()}) to run any due delayed triggers. */
    public static void tickAll(long currentTick) {
        if (PENDING.isEmpty()) return;

        for (List<ScheduledTask> tasks : PENDING.values()) {
            tasks.removeIf(scheduled -> {
                if (scheduled.targetTick() > currentTick) return false;
                try {
                    scheduled.task().run();
                } catch (Exception e) {
                    CreateAeronauticsDiscovery.LOGGER.error("[MarkerNetwork] Delayed trigger task failed", e);
                }
                return true;
            });
        }
        PENDING.values().removeIf(List::isEmpty);
    }

    /** Clears any pending delayed triggers for a sub-level - call this when a flyover is removed. */
    public static void clear(UUID subLevelId) {
        PENDING.remove(subLevelId);
    }

    private static void schedule(UUID subLevelId, long targetTick, Runnable task) {
        PENDING.computeIfAbsent(subLevelId, id -> new ArrayList<>()).add(new ScheduledTask(targetTick, task));
    }

    private static void fire(MarkerEntity marker, MarkerTrigger trigger) {
        if (!marker.isAlive() || !marker.isBound()) return;
        MarkerBehavior<?> behavior = marker.resolveBehavior();
        if (behavior != null) {
            behavior.onTrigger(marker, trigger);
        }
    }

    private static List<MarkerEntity> resolveBoundMarkers(ServerLevel level, UUID subLevelId, Vec3 originWorldPos, double searchRadius) {
        AABB bounds = new AABB(
                originWorldPos.x - searchRadius, originWorldPos.y - searchRadius, originWorldPos.z - searchRadius,
                originWorldPos.x + searchRadius, originWorldPos.y + searchRadius, originWorldPos.z + searchRadius);
        return level.getEntitiesOfClass(MarkerEntity.class, bounds, marker ->
                subLevelId.equals(marker.getPersistentData().getUUID(FLYOVER_ID_TAG)) && marker.isBound());
    }
}