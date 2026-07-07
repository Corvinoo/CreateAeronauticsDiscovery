package me.corvino.aeronauticsdiscovery.marker;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

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
                                     double searchRadius, double propagationBlocksPerTick,
                                     long currentTick) {
        UUID key = subLevelId != null ? subLevelId : WORLD_BOUND_KEY;

        for (MarkerEntity marker : resolveBoundMarkers(level, key, trigger.originWorldPos(), searchRadius)) {
            double distance = marker.position().distanceTo(trigger.originWorldPos());

            if (propagationBlocksPerTick <= 0) {
                fire(marker, trigger);
            } else {
                long delayTicks = (long) Math.ceil(distance / propagationBlocksPerTick);
                schedule(key, currentTick + delayTicks, () -> fire(marker, trigger));
            }
        }
    }


    /** Call once per server tick (e.g. from {@code FlyoverManager#tick()}) to run any due delayed triggers. */
    public static void tickAll(long currentTick) {
        if (PENDING.isEmpty()) return;

        List<Runnable> due = new ArrayList<>();
        for (List<ScheduledTask> tasks : PENDING.values()) {
            Iterator<ScheduledTask> it = tasks.iterator();
            while (it.hasNext()) {
                ScheduledTask scheduled = it.next();
                if (scheduled.targetTick() <= currentTick) {
                    due.add(scheduled.task());
                    it.remove();
                }
            }
        }
        PENDING.values().removeIf(List::isEmpty);

        // Run only after we're fully done reading/mutating PENDING above
        for (Runnable task : due) {
            try {
                task.run();
            } catch (Exception e) {
                CreateAeronauticsDiscovery.LOGGER.error("[MarkerNetwork] Delayed trigger task failed", e);
            }
        }
    }

    /** Public entry point for triggering a single specific marker from external systems (projectile, collision, etc.). */
    public static void triggerDirect(MarkerEntity marker, MarkerTrigger trigger) {
        fire(marker, trigger);
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
        marker.discard();
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

        // DEBUG: search parameters 
        String entityGetterClass = level.getEntities().getClass().getSimpleName();
        System.out.println("[MARKER_DEBUG] resolveBoundMarkers called"
                + " key=" + key
                + " origin=" + originWorldPos
                + " radius=" + searchRadius
                + " bounds=" + bounds
                + " entityGetter=" + entityGetterClass);

        // DEBUG: dump ALL markers in the level via getAll() 
        int allCount = 0;
        for (Entity e : level.getEntities().getAll()) {
            if (e instanceof MarkerEntity m) {
                allCount++;
                CompoundTag d = m.getPersistentData();
                UUID tag = d.hasUUID(FLYOVER_ID_TAG) ? d.getUUID(FLYOVER_ID_TAG) : null;
                System.out.println("[MARKER_DEBUG] getAll: id=" + m.getBehaviorId()
                        + " pos=" + m.position()
                        + " level=" + m.level().getClass().getName()
                        + " isSL=" + (m.level() instanceof ServerLevel)
                        + " isAlive=" + m.isAlive()
                        + " isBound=" + m.isBound()
                        + " tag=" + tag);
            }
        }
        System.out.println("[MARKER_DEBUG] getAll found " + allCount + " MarkerEntity total in level");

        //  DEBUG: raw AABB query (before predicate filtering) 
        List<MarkerEntity> rawAabb = level.getEntitiesOfClass(MarkerEntity.class, bounds, m -> true);
        System.out.println("[MARKER_DEBUG] AABB query returned " + rawAabb.size() + " markers (unfiltered)");
        for (MarkerEntity m : rawAabb) {
            CompoundTag d = m.getPersistentData();
            UUID tag = d.hasUUID(FLYOVER_ID_TAG) ? d.getUUID(FLYOVER_ID_TAG) : null;
            System.out.println("[MARKER_DEBUG] AABB raw: id=" + m.getBehaviorId()
                    + " pos=" + m.position()
                    + " level=" + m.level().getClass().getName()
                    + " isSL=" + (m.level() instanceof ServerLevel)
                    + " isAlive=" + m.isAlive()
                    + " isBound=" + m.isBound()
                    + " tag=" + tag
                    + " distance=" + m.position().distanceTo(originWorldPos));
        }

        //  DEBUG: step-by-step filtering 
        List<MarkerEntity> results = new ArrayList<>();
        for (MarkerEntity marker : rawAabb) {
            CompoundTag data = marker.getPersistentData();
            UUID existingId = data.contains(FLYOVER_ID_TAG) ? data.getUUID(FLYOVER_ID_TAG) : null;

            boolean tagMatches;
            if (WORLD_BOUND_KEY.equals(key)) {
                tagMatches = existingId == null;
            } else {
                tagMatches = key.equals(existingId);
            }
            boolean bound = marker.isBound();
            boolean passes = tagMatches && bound;

            System.out.println("[MARKER_DEBUG] filter: id=" + marker.getBehaviorId()
                    + " pos=" + marker.position()
                    + " existingId=" + existingId
                    + " key=" + key
                    + " tagMatches=" + tagMatches
                    + " isBound=" + bound
                    + " PASSES=" + passes);

            if (passes) {
                results.add(marker);
            }
        }
        System.out.println("[MARKER_DEBUG] resolveBoundMarkers returning " + results.size() + " markers");
        return results;
    }
}
