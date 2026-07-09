package me.corvino.aeronauticsdiscovery.pin;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.pin.behaviour.PinBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;

/**
 * Handles "pins aware of other pins" without ever giving one pin a direct reference to another.
 * <p>
 * Direct entity-to-entity references (cached UUIDs, held Entity pointers) go stale across chunk
 * unload/reload and get expensive fast if every pin independently scans the world every tick for
 * neighbours. Instead:
 * <ul>
 *   <li>Membership in a flyover's pin set is never stored explicitly - it's resolved on demand by
 *   querying a world-space region around the trigger for {@link PinEntity} instances tagged with that
 *   sub-level's {@link me.corvino.aeronauticsdiscovery.util.SubLevelTags#SUBLEVEL_ID_TAG},
 *   exactly the same convention {@code SeatPopulator} already uses for traders.</li>
 *   <li>A trigger (explosion, external force, proximity) is posted once via {@link #notifyTrigger}, which
 *   does a single distance pass over the relevant pins and schedules delayed callbacks for the ones
 *   that should react - rather than each pin polling its surroundings every tick.</li>
 * </ul>
 * Pins without a sub-level tag (world-bound pins, e.g. placed manually with the wand on a normal
 * block) are grouped under {@link #WORLD_BOUND_KEY} internally. Their triggers propagate spatially within
 * the search radius, with no sub-level grouping.
 */
public final class PinNetwork {
    private PinNetwork() {}

    /** Sentinel UUID for world-bound pins (those without {@code SUBLEVEL_ID_TAG}). */
    private static final UUID WORLD_BOUND_KEY = new UUID(0, 0);

    private record ScheduledTask(long targetTick, Runnable task) {}

    private static final Map<UUID, List<ScheduledTask>> PENDING = new HashMap<>();

    /**
     * Posts a trigger to pins bound to a sub-level, or to world-bound pins when {@code subLevelId}
     * is {@code null}. World-bound pins are resolved purely by spatial proximity, with no sub-level
     * grouping.
     */
    public static void notifyTrigger(ServerLevel level, UUID subLevelId, PinTrigger trigger,
                                     double searchRadius, double propagationBlocksPerTick,
                                     long currentTick) {
        UUID key = subLevelId != null ? subLevelId : WORLD_BOUND_KEY;

        for (PinEntity pin : resolveBoundPins(level, key, trigger.originWorldPos(), searchRadius)) {
            double distance = pin.position().distanceTo(trigger.originWorldPos());

            if (propagationBlocksPerTick <= 0) {
                fire(pin, trigger);
            } else {
                long delayTicks = (long) Math.ceil(distance / propagationBlocksPerTick);
                schedule(key, currentTick + delayTicks, () -> fire(pin, trigger));
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
                CreateAeronauticsDiscovery.LOGGER.error("[PinNetwork] Delayed trigger task failed", e);
            }
        }
    }

    /** Public entry point for triggering a single specific pin from external systems (projectile, collision, etc.). */
    public static void triggerDirect(PinEntity pin, PinTrigger trigger) {
        fire(pin, trigger);
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

    private static void fire(PinEntity pin, PinTrigger trigger) {
        if (!pin.isAlive() || !pin.isBound()) return;
        if (!pin.getTriggerMask().accepts(trigger.kind())) return;

        // Capture emitter info before discard
        EmitterConfig emitter = pin.getEmitterConfig();
        Vec3 pos = pin.position();
        CompoundTag data = pin.getPersistentData();
        UUID subLevelId = data.hasUUID(SUBLEVEL_ID_TAG) ? data.getUUID(SUBLEVEL_ID_TAG) : null;
        ServerLevel level = pin.level() instanceof ServerLevel sl ? sl : null;

        pin.discard();
        PinBehavior<?> behavior = pin.resolveBehavior();
        if (behavior != null) {
            behavior.onTrigger(pin, trigger);
        }

        // Propagate to nearby pins if this pin is an emitter
        if (emitter.isEnabled() && level != null) {
            PinTrigger propagated = new PinTrigger(trigger.kind(), pos);
            notifyTrigger(level, subLevelId, propagated,
                    emitter.radius(), emitter.propagationSpeed(), level.getGameTime());
        }
    }

    /**
     * Resolves pins matching the given key:
     * <ul>
     *   <li>{@link #WORLD_BOUND_KEY} - pins without {@code SUBLEVEL_ID_TAG} that are {@link PinEntity#isBound()}</li>
     *   <li>Any other UUID - pins whose persistent data contains a matching {@code SUBLEVEL_ID_TAG}</li>
     * </ul>
     */
    private static List<PinEntity> resolveBoundPins(ServerLevel level, UUID key, Vec3 originWorldPos, double searchRadius) {
        AABB bounds = new AABB(
                originWorldPos.x - searchRadius, originWorldPos.y - searchRadius, originWorldPos.z - searchRadius,
                originWorldPos.x + searchRadius, originWorldPos.y + searchRadius, originWorldPos.z + searchRadius);

        return level.getEntitiesOfClass(PinEntity.class, bounds, pin -> {
            CompoundTag data = pin.getPersistentData();
            UUID existingId = data.contains(SUBLEVEL_ID_TAG) ? data.getUUID(SUBLEVEL_ID_TAG) : null;
            if (WORLD_BOUND_KEY.equals(key)) {
                return existingId == null && pin.isBound();
            }
            return key.equals(existingId) && pin.isBound();
        });
    }
}
