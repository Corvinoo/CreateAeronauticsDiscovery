package me.corvino.aeronauticsdiscovery.physics;

import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.Balloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.ServerBalloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.BalloonMap;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import net.minecraft.core.Registry;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.BUOYANCY;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds newly assembled SubLevels stationary (suppressing all forces and
 * countering gravity) until their own computed lift is sufficient to support their weight
 * then releases them to fall naturally into genuine equilibrium.
 *
 * <p>One instance per level. Registered against {@link BuoyancyStabilizationEvents}.</p>
 */
public final class BuoyancyStabilizationManager {

    private static final Map<ServerLevel, BuoyancyStabilizationManager> INSTANCES = new ConcurrentHashMap<>();

    private final ServerLevel level;
    private final Map<UUID, Stabilizer> active = new ConcurrentHashMap<>();

    private BuoyancyStabilizationManager(ServerLevel level) {
        this.level = level;
    }

    public static BuoyancyStabilizationManager get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, BuoyancyStabilizationManager::new);
    }

    // Public API; driven by StabilizeBuoyancyStep
    public boolean isTracking(UUID subLevelId) {
        return active.containsKey(subLevelId);
    }

    public void beginStabilizing(ServerSubLevel subLevel, BuoyancyStabilizationConfig config) {
        active.put(subLevel.getUniqueId(), new Stabilizer(subLevel, config));
    }

    private Balloon findBalloon(ServerSubLevel subLevel) {
        BalloonMap balloonMap = BalloonMap.MAP.get(level);
        int totalBalloons = 0;
        for (Balloon balloon : balloonMap.getBalloons()) {
            totalBalloons++;
            SubLevel balloonSubLevel = Sable.HELPER.getContaining(level, balloon.getControllerPos());
            if (balloonSubLevel == null) {
                ModLog.debug(BUOYANCY, "balloon {} has null sublevel at {}", balloon.getControllerPos(), level.dimension().location());
                continue;
            }
            if (balloonSubLevel.getUniqueId().equals(subLevel.getUniqueId())) {
                ModLog.debug(BUOYANCY, "found balloon {} (heaters={}, isValid={}) for sublevel {}",
                        balloon.getControllerPos(), balloon.getHeaters().size(), balloon.isValid(), subLevel.getUniqueId());
                return balloon;
            }
        }
        ModLog.debug(BUOYANCY, "findBalloon: scanned {} balloon(s) in map, found NONE for sublevel {}",
                totalBalloons, subLevel.getUniqueId());
        return null;
    }

    /**
     * Consumes the "stabilized" flag; true exactly once, the first time it's
     * observed after release
     */
    public boolean pollStabilized(UUID subLevelId) {
        Stabilizer stabilizer = active.get(subLevelId);
        if (stabilizer == null || !stabilizer.stabilized) return false;
        active.remove(subLevelId);
        return true;
    }

    public void cancel(UUID subLevelId) {
        active.remove(subLevelId);
    }

    // Physics-thread entry point, called once per substep
    void tickSubstep(double timeStep) {
        if (active.isEmpty()) return;

        double gravityMagnitude = Math.abs(DimensionPhysicsData.getGravity(level).y);

        Iterator<Stabilizer> it = active.values().iterator();
        while (it.hasNext()) {
            Stabilizer stabilizer = it.next();
            if (stabilizer.subLevel.isRemoved()) {
                it.remove();
                continue;
            }
            if (stabilizer.stabilized) continue; // awaiting consumption by the step
            evaluateSubstep(stabilizer, gravityMagnitude, timeStep);
        }
    }

    private void evaluateSubstep(Stabilizer stabilizer, double gravityMagnitude, double timeStep) {
        ServerSubLevel subLevel = stabilizer.subLevel;
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) {
            active.remove(subLevel.getUniqueId());
            return;
        }

        Balloon balloon = stabilizer.balloon;
        if (balloon == null) {
            balloon = findBalloon(subLevel);
            stabilizer.balloon = balloon;
            if (balloon == null) {
                ModLog.debug(BUOYANCY, "{} substep {}s - still no balloon found",
                        subLevel.getUniqueId(), (int) stabilizer.elapsedSeconds);
            }
        }
        if (balloon != null) {
            if (balloon instanceof ServerBalloon serverBalloon) {
                double target = serverBalloon.getTotalTargetVolume();
                if (target > stabilizer.peakTargetVolume) {
                    stabilizer.peakTargetVolume = target;
                }
                if (stabilizer.peakTargetVolume > 0.05 && target <= 0.05) {
                    ModLog.debug(BUOYANCY, "{} target volume dropped from {} to {} - releasing",
                            subLevel.getUniqueId(), String.format("%.2f", stabilizer.peakTargetVolume), String.format("%.2f", target));
                    releaseCleanly(handle);
                    stabilizer.stabilized = true;
                    return;
                }
            }
            if (!balloon.isValid()) {
                ModLog.debug(BUOYANCY, "{} balloon invalid - releasing",
                        subLevel.getUniqueId());
                releaseCleanly(handle);
                stabilizer.stabilized = true;
                return;
            }
        }

        double weightImpulse = subLevel.getMassTracker().getMass() * gravityMagnitude * timeStep;
        double liftImpulseY = readWorldSpaceLiftImpulseY(subLevel);

        boolean qualifies = liftImpulseY >= weightImpulse * stabilizer.config.liftSafetyMargin();
        stabilizer.consecutiveQualifyingSubsteps = qualifies ? stabilizer.consecutiveQualifyingSubsteps + 1 : 0;
        stabilizer.elapsedSeconds += timeStep;

        boolean reachedStability = stabilizer.consecutiveQualifyingSubsteps >= stabilizer.config.requiredStableSubsteps();
        boolean timedOut = stabilizer.elapsedSeconds >= stabilizer.config.maxHoldSeconds();

        if (reachedStability || timedOut) {
            if (timedOut && !reachedStability) {
                ModLog.debug(
                        BUOYANCY, "{} released after {}s without reaching stable lift",
                        subLevel.getUniqueId(), stabilizer.config.maxHoldSeconds());
            }
            releaseCleanly(handle);
            stabilizer.stabilized = true;
            return; // do NOT suppress this substep; this substep's already-computed forces apply normally
        }

        suppressThisSubstep(subLevel, handle, gravityMagnitude, timeStep);
    }

    private static final ResourceKey<Registry<ForceGroup>> FORCE_GROUP_REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("sable", "force_groups"));
    private static final ResourceLocation BALLOON_LIFT_ID =
            ResourceLocation.fromNamespaceAndPath("sable", "balloon_lift");

    private ForceGroup balloonLift() {
        Registry<ForceGroup> registry = level.registryAccess()
                .registry(FORCE_GROUP_REGISTRY).orElse(null);
        return registry != null ? registry.get(BALLOON_LIFT_ID) : null;
    }

    /**
     * Reads BALLOON_LIFT's accumulated impulse and rotates it into world space.
     * ForceTotal#getLocalForce() should be body-local
     */
    private double readWorldSpaceLiftImpulseY(ServerSubLevel subLevel) {
        ForceGroup lift = balloonLift();
        if (lift == null) return 0;
        QueuedForceGroup liftGroup = subLevel.getOrCreateQueuedForceGroup(lift);
        Vector3d local = liftGroup.getForceTotal().getLocalForce();
        Quaterniond orientation = subLevel.logicalPose().orientation();
        Vector3d world = orientation.transform(new Vector3d(local), new Vector3d());
        return world.y;
    }

    private void suppressThisSubstep(ServerSubLevel subLevel, RigidBodyHandle handle, double gravityMagnitude, double timeStep) {
        var forceGroups = subLevel.getQueuedForceGroups();
        if (forceGroups != null) {
            forceGroups.values().forEach(QueuedForceGroup::reset);
        }

        Vector3d velocity = handle.getLinearVelocity(new Vector3d());
        Vector3d angularVelocity = handle.getAngularVelocity(new Vector3d());
        handle.addLinearAndAngularVelocity(velocity.negate(), angularVelocity.negate());
        handle.addLinearAndAngularVelocity(new Vector3d(0, gravityMagnitude * timeStep, 0), new Vector3d());
    }

    private void releaseCleanly(RigidBodyHandle handle) {
        // Zero residual velocity so the transition into real physics starts from rest;
        // the lift force already computed this substep is left untouched and will apply normally
        Vector3d velocity = handle.getLinearVelocity(new Vector3d());
        Vector3d angularVelocity = handle.getAngularVelocity(new Vector3d());
        handle.addLinearAndAngularVelocity(velocity.negate(), angularVelocity.negate());
    }

    private static final class Stabilizer {
        final ServerSubLevel subLevel;
        final BuoyancyStabilizationConfig config;
        Balloon balloon;
        double peakTargetVolume;
        int consecutiveQualifyingSubsteps = 0;
        double elapsedSeconds = 0;
        boolean stabilized = false;

        Stabilizer(ServerSubLevel subLevel, BuoyancyStabilizationConfig config) {
            this.subLevel = subLevel;
            this.config = config;
        }
    }
}