package me.corvino.aeronauticsdiscovery.marker;

import dev.ryanhcode.sable.sublevel.SubLevel;
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
 *   Membership in a flyover's marker set is never stored explicitly - it's resolved on demand by
 *   querying the sub-level's current bounds for {@link MarkerEntity} instances tagged with that
 *   sub-level's {@link me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager#FLYOVER_ID_TAG},
 *   A trigger (explosion, external force, proximity) is posted once via {@link #notifyTrigger}, which
 *   does a single distance pass over that flyover's markers and schedules delayed callbacks for the ones
 *   that should react.
 */
public final class MarkerNetwork {
    private MarkerNetwork() {}

    private record ScheduledTask(long targetTick, Runnable task) {}

    private static final Map<UUID, List<ScheduledTask>> PENDING = new HashMap<>();

    /**
     * Posts a trigger to every marker bound to {@code subLevel}. Markers within {@code immediateRadius}
     * (plot-local units) react immediately; every bound marker beyond that still gets notified, but after
     * a delay of {@code distance / propagationSpeedPerTick} ticks - giving a natural "explosion travels
     * outward" chain-reaction feel without any marker needing to know about any other.
     */
    public static void notifyTrigger(ServerLevel level, SubLevel subLevel, MarkerTrigger trigger,
                                      double immediateRadius, double propagationBlocksPerTick,
                                      long currentTick, int maxChainDepth) {
        if (trigger.chainDepth() > maxChainDepth) return;

        UUID subLevelId = subLevel.getUniqueId();
        for (MarkerEntity marker : resolveBoundMarkers(level, subLevel, subLevelId)) {
            Vec3 markerPlotPos = marker.isBound() ? marker.position() : null;
            if (markerPlotPos == null) continue;

            double distance = markerPlotPos.distanceTo(trigger.originPlotPos());

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

    private static List<MarkerEntity> resolveBoundMarkers(ServerLevel level, SubLevel subLevel, UUID subLevelId) {
        AABB bounds = subLevel.getPlot().getBoundingBox().toAABB().inflate(2.0);
        return level.getEntitiesOfClass(MarkerEntity.class, bounds, marker ->
                subLevelId.equals(marker.getPersistentData().getUUID(FLYOVER_ID_TAG)) && marker.isBound());
    }
}
