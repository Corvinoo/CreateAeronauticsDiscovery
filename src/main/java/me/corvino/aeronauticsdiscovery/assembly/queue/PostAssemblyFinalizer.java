package me.corvino.aeronauticsdiscovery.assembly.queue;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotPlan;
import me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.PHYSICS;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.AUTOPILOT;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import me.corvino.aeronauticsdiscovery.physics.PrefabPhysicsConfig;
import me.corvino.aeronauticsdiscovery.physics.PrefabPhysicsRegistry;
import me.corvino.aeronauticsdiscovery.util.SubLevelTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Finalization steps run once after a pipeline reports {@code SUCCESS}.
 * Unlike pipeline-level {@code PostAssemblyStep}s (which are template/pipeline
 * specific), these always run for every successful assembly regardless of
 * which pipeline produced it.
 */
final class PostAssemblyFinalizer {

    private PostAssemblyFinalizer() {
    }

    static void run(ServerLevel level, AssemblyContext ctx) {
        ServerSubLevel subLevel = resolveSubLevel(ctx);
        if (subLevel == null) return;

        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (!handle.isValid()) return;

//        teleportToYaw(ctx, handle); moved into a step

        // maybe this one too should be into a step
        applyInitialVelocity(ctx, handle);
        nameSubLevel(ctx, subLevel);
        tagSublevel(subLevel, ctx);
        registerAsFlyoverIfRequested(level, ctx, subLevel);
    }

    private static ServerSubLevel resolveSubLevel(AssemblyContext ctx) {
        if (ctx.assemblyResult == null) return null;
        return ctx.assemblyResult.subLevel() instanceof ServerSubLevel subLevel ? subLevel : null;
    }

    private static void teleportToYaw(AssemblyContext ctx, RigidBodyHandle handle) {
        if (ctx.yawRadians == 0.0) return;
        Vector3d bodyPos = new Vector3d(
                ctx.templateBounds().minX() + (ctx.templateBounds().maxX() - ctx.templateBounds().minX() + 1) / 2.0,
                ctx.templateBounds().minY() + (ctx.templateBounds().maxY() - ctx.templateBounds().minY() + 1) / 2.0,
                ctx.templateBounds().minZ() + (ctx.templateBounds().maxZ() - ctx.templateBounds().minZ() + 1) / 2.0
        );
        handle.teleport(bodyPos, new Quaterniond().rotationY(ctx.yawRadians));
    }

    private static void applyInitialVelocity(AssemblyContext ctx, RigidBodyHandle handle) {
        InitialVelocity velocity = resolveVelocity(ctx);
        if (velocity == null || velocity.equals(InitialVelocity.NONE)) return;

        Vec3 linear = velocity.linear();
        Vec3 angular = velocity.angular();
        if (ctx.yawRadians != 0.0) {
            linear = rotateVec3(linear, ctx.yawRadians);
            angular = rotateVec3(angular, ctx.yawRadians);
        }

        ModLog.info(PHYSICS,
                "Applying velocity to '{}': linear={}, angular={}, impulse={}",
                ctx.templateId, linear, angular, velocity.impulse());

        if (velocity.impulse()) {
            handle.applyLinearAndAngularImpulse(
                    new Vector3d(linear.x, linear.y, linear.z),
                    new Vector3d(angular.x, angular.y, angular.z));
        } else {
            handle.addLinearAndAngularVelocity(
                    new Vector3d(linear.x, linear.y, linear.z),
                    new Vector3d(angular.x, angular.y, angular.z));
        }
    }

    private static InitialVelocity resolveVelocity(AssemblyContext ctx) {
        if (ctx.velocityOverride != null && !ctx.velocityOverride.equals(InitialVelocity.NONE)) {
            return ctx.velocityOverride;
        }
        return PrefabPhysicsRegistry.getInstance().get(ctx.templateId)
                .map(PrefabPhysicsConfig::initialVelocity)
                .orElse(InitialVelocity.NONE);
    }

    private static Vec3 rotateVec3(Vec3 vec, double yawRadians) {
        if (yawRadians == 0.0) return vec;
        double cos = Math.cos(yawRadians);
        double sin = Math.sin(yawRadians);
        return new Vec3(
                vec.x * cos + vec.z * sin,
                vec.y,
                -vec.x * sin + vec.z * cos
        );
    }

    private static void nameSubLevel(AssemblyContext ctx, ServerSubLevel subLevel) {
        String name = ctx.subLevelName != null ? ctx.subLevelName : ctx.templateId.getPath();
        subLevel.setName(name);
    }

    private static void tagSublevel(ServerSubLevel subLevel, AssemblyContext ctx) {
        CompoundTag tag = new CompoundTag();
        tag.putString("mod_id", CreateAeronauticsDiscovery.MODID);
        tag.putString("template_id", ctx.templateId.toString());
        AutopilotPlan plan = resolvePlan(ctx);
        if (plan != null) {
            AutopilotPlan.CODEC.codec()
                    .encodeStart(NbtOps.INSTANCE, plan)
                    .resultOrPartial(error -> ModLog.warn(AUTOPILOT, "Failed to serialize craft plan: {}", error))
                    .ifPresent(value -> tag.put(SubLevelTags.PLAN_TAG, value));
        }
        subLevel.setUserDataTag(tag);
    }

    private static AutopilotPlan resolvePlan(AssemblyContext ctx) {
        if (ctx.planOverride != null) return ctx.planOverride;
        return PrefabPhysicsRegistry.getInstance().get(ctx.templateId)
                .map(PrefabPhysicsConfig::plan)
                .orElse(null);
    }

    private static void registerAsFlyoverIfRequested(ServerLevel level, AssemblyContext ctx, ServerSubLevel subLevel) {
        if (ctx.registerAsFlyover) {
            FlyoverManager.get(level).addFlyover(subLevel, ctx.templateId);
        }
    }
}