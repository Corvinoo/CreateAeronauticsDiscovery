package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyStep;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import me.corvino.aeronauticsdiscovery.physics.PrefabPhysicsConfig;
import me.corvino.aeronauticsdiscovery.physics.PrefabPhysicsRegistry;
import net.minecraft.world.phys.Vec3;

public class ApplyVelocityStep implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        if (ctx.assemblyResult == null) return AssemblyResult.SUCCESS;

        InitialVelocity vel = resolveVelocity(ctx);
        if (vel == null || vel.equals(InitialVelocity.NONE)) return AssemblyResult.SUCCESS;

        Vec3 linear = vel.linear();
        Vec3 angular = vel.angular();
        if (ctx.yawRadians != 0.0) {
            linear = rotateVec3(linear, ctx.yawRadians);
            angular = rotateVec3(angular, ctx.yawRadians);
        }

        RigidBodyHandle handle = RigidBodyHandle.of((ServerSubLevel) ctx.assemblyResult.subLevel());

        CreateAeronauticsDiscovery.LOGGER.info("[PHYSICS] Applying velocity to '{}': linear={}, angular={}, impulse={}",
                ctx.templateId, linear, angular, vel.impulse());

        if (vel.impulse()) {
            handle.applyLinearAndAngularImpulse(
                    new org.joml.Vector3d(linear.x, linear.y, linear.z),
                    new org.joml.Vector3d(angular.x, angular.y, angular.z)
            );
        } else {
            handle.addLinearAndAngularVelocity(
                    new org.joml.Vector3d(linear.x, linear.y, linear.z),
                    new org.joml.Vector3d(angular.x, angular.y, angular.z)
            );
        }

        return AssemblyResult.SUCCESS;
    }

    private static InitialVelocity resolveVelocity(AssemblyContext ctx) {
        if (ctx.velocityOverride != null && !ctx.velocityOverride.equals(InitialVelocity.NONE)) {
            return ctx.velocityOverride;
        }
        return PrefabPhysicsRegistry.getInstance().get(ctx.templateId)
                .map(PrefabPhysicsConfig::initialVelocity)
                .orElse(InitialVelocity.NONE);
    }

    static Vec3 rotateVec3(Vec3 vec, double yawRadians) {
        if (yawRadians == 0.0) return vec;
        double cos = Math.cos(yawRadians);
        double sin = Math.sin(yawRadians);
        return new Vec3(
                vec.x * cos + vec.z * sin,
                vec.y,
                -vec.x * sin + vec.z * cos
        );
    }
}
