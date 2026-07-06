package me.corvino.aeronauticsdiscovery.marker;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

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
 *   does a single distance pass over the relevant markers and schedules delayed callbacks for the ones
 *   that should react - rather than each marker polling its surroundings every tick.</li>
 * </ul>
 * Markers without a sub-level tag (world-bound markers, e.g. placed manually with the wand on a normal
 * block) are grouped under {@link #WORLD_BOUND_KEY} internally. Their triggers propagate spatially within
 * the search radius, with no sub-level grouping.
 */
public final class MarkerNetwork {
    private MarkerNetwork() {}

    /** Sentinel UUID for world-bound markers (those without {@code FLYOVER_ID_TAG}). */
    private static final UUID WORLD_BOUND_KEY = new UUID(0, 0);

    private record ScheduledTask(long targetTick, Runnable task) {}

    private static final Map<UUID, List<ScheduledTask>> PENDING = new HashMap<>();

    /**
     * Posts a trigger to markers bound to a sub-level, or to world-bound markers when {@code subLevelId}
     * is {@code null}. World-bound markers are resolved purely by spatial proximity, with no sub-level
     * grouping.
     */
    public static void notifyTrigger(ServerLevel level, UUID subLevelId, MarkerTrigger trigger,
                                     double searchRadius, double immediateRadius, double propagationBlocksPerTick,
                                     long currentTick, int maxChainDepth) {
        if (trigger.chainDepth() > maxChainDepth) return;

        UUID key = subLevelId != null ? subLevelId : WORLD_BOUND_KEY;

        for (MarkerEntity marker : resolveBoundMarkers(level, key, trigger.originWorldPos(), searchRadius)) {
            double distance = marker.position().distanceTo(trigger.originWorldPos());

            if (distance <= immediateRadius) {
                fire(marker, trigger);
            } else if (propagationBlocksPerTick > 0) {
                long delayTicks = (long) Math.ceil(distance / propagationBlocksPerTick);
                schedule(key, currentTick + delayTicks, () -> fire(marker, trigger.withDepth(trigger.chainDepth() + 1)));
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

    /** Clears any pending delayed triggers for a sub-level. Does not affect world-bound pending tasks. */
    public static void clear(UUID subLevelId) {
        PENDING.remove(subLevelId);
    }

    /** Tick listener registered in {@code CreateAeronauticsDiscovery}. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            tickAll(serverLevel.getGameTime());
        }
    }

    private static void schedule(UUID key, long targetTick, Runnable task) {
        PENDING.computeIfAbsent(key, id -> new ArrayList<>()).add(new ScheduledTask(targetTick, task));
    }

    private static void fire(MarkerEntity marker, MarkerTrigger trigger) {
        if (!marker.isAlive() || !marker.isBound()) return;
        MarkerBehavior<?> behavior = marker.resolveBehavior();
        if (behavior != null) {
            behavior.onTrigger(marker, trigger);
        }
    }

    /**
     * Resolves markers matching the given key:
     * <ul>
     *   <li>{@link #WORLD_BOUND_KEY} - markers without {@code FLYOVER_ID_TAG} that are {@link MarkerEntity#isBound()}</li>
     *   <li>Any other UUID - markers whose persistent data contains a matching {@code FLYOVER_ID_TAG}</li>
     * </ul>
     */
    private static List<MarkerEntity> resolveBoundMarkers(ServerLevel level, UUID key, Vec3 originWorldPos, double searchRadius) {
        AABB bounds = new AABB(
                originWorldPos.x - searchRadius, originWorldPos.y - searchRadius, originWorldPos.z - searchRadius,
                originWorldPos.x + searchRadius, originWorldPos.y + searchRadius, originWorldPos.z + searchRadius);

        return level.getEntitiesOfClass(MarkerEntity.class, bounds, marker -> {
            UUID existingId = marker.getPersistentData().getUUID(FLYOVER_ID_TAG);
            if (WORLD_BOUND_KEY.equals(key)) {
                return existingId == null && marker.isBound();
            }
            return key.equals(existingId) && marker.isBound();
        });
    }
}
