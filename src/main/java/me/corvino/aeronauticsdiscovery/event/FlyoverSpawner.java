package me.corvino.aeronauticsdiscovery.event;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.PrefabService;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import me.corvino.aeronauticsdiscovery.physics.PrefabPhysicsConfig;
import me.corvino.aeronauticsdiscovery.physics.PrefabPhysicsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class FlyoverSpawner {
    private FlyoverSpawner() {}

    public static SimAssemblyHelper.AssemblyResult spawn(
            ServerLevel level, FlyoverEventConfig config, BlockPos pos, double yawRadians
    ) throws Exception {
        StructureTemplate template = PrefabService.loadPrefab(level, config.template());

        SimAssemblyHelper.AssemblyResult result = PrefabService.placeAndAssemble(
                level, template, pos, config.template(), Rotation.NONE, false
        );

        rotateAssembledBodyContinuous(result, template, pos, yawRadians);
        applyVelocityContinuous(level, result, config, yawRadians);

        result.subLevel().setName("flyover");
        FlyoverManager.get(level).addFlyover(result.subLevel(), config.template());

        return result;
    }

    private static void rotateAssembledBodyContinuous(
            SimAssemblyHelper.AssemblyResult result, StructureTemplate template,
            BlockPos pos, double yawRadians
    ) {
        if (!(result.subLevel() instanceof ServerSubLevel serverSubLevel)) return;
        RigidBodyHandle handle = RigidBodyHandle.of(serverSubLevel);
        if (handle == null || !handle.isValid()) return;

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE);
        BoundingBox bounds = template.getBoundingBox(settings, pos);
        Vector3d bodyPos = new Vector3d(
                bounds.minX() + (bounds.maxX() - bounds.minX() + 1) / 2.0,
                bounds.minY() + (bounds.maxY() - bounds.minY() + 1) / 2.0,
                bounds.minZ() + (bounds.maxZ() - bounds.minZ() + 1) / 2.0
        );
        handle.teleport(bodyPos, new Quaterniond().rotationY(yawRadians));
    }

    private static void applyVelocityContinuous(ServerLevel level, SimAssemblyHelper.AssemblyResult result,
                                                  FlyoverEventConfig config, double yawRadians) {
        InitialVelocity velocity = resolveVelocity(config);
        if (velocity.equals(InitialVelocity.NONE)) return;

        Vec3 linear = rotateVec3(velocity.linear(), yawRadians);
        Vec3 angular = rotateVec3(velocity.angular(), yawRadians);

        RigidBodyHandle handle = RigidBodyHandle.of((ServerSubLevel) result.subLevel());

        CreateAeronauticsDiscovery.LOGGER.info("[FLYOVER] Applying velocity to '{}' (yaw={} rad): linear={}, angular={}, impulse={}",
                config.template(), String.format("%.3f", yawRadians), linear, angular, velocity.impulse());

        if (velocity.impulse()) {
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
    }

    private static InitialVelocity resolveVelocity(FlyoverEventConfig config) {
        InitialVelocity velocity = config.velocity();
        if (velocity == null || velocity.equals(InitialVelocity.NONE)) {
            velocity = PrefabPhysicsRegistry.getInstance()
                    .get(config.template())
                    .map(PrefabPhysicsConfig::initialVelocity)
                    .orElse(InitialVelocity.NONE);
        }
        return velocity;
    }

    public static Vec3 rotateVec3(Vec3 vec, double yawRadians) {
        double cos = Math.cos(yawRadians);
        double sin = Math.sin(yawRadians);
        return new Vec3(
                vec.x * cos + vec.z * sin,
                vec.y,
                -vec.x * sin + vec.z * cos
        );
    }

}
