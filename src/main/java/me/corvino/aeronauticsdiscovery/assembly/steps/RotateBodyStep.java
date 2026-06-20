package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyStep;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public class RotateBodyStep implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        if (ctx.yawRadians == 0.0 || ctx.assemblyResult == null || ctx.bounds == null) {
            return AssemblyResult.SUCCESS;
        }
        if (!(ctx.assemblyResult.subLevel() instanceof ServerSubLevel serverSubLevel)) {
            return AssemblyResult.SUCCESS;
        }

        RigidBodyHandle handle = RigidBodyHandle.of(serverSubLevel);
        if (handle == null || !handle.isValid()) {
            return AssemblyResult.SUCCESS;
        }

        Vector3d bodyPos = new Vector3d(
                ctx.bounds.minX() + (ctx.bounds.maxX() - ctx.bounds.minX() + 1) / 2.0,
                ctx.bounds.minY() + (ctx.bounds.maxY() - ctx.bounds.minY() + 1) / 2.0,
                ctx.bounds.minZ() + (ctx.bounds.maxZ() - ctx.bounds.minZ() + 1) / 2.0
        );

        handle.teleport(bodyPos, new Quaterniond().rotationY(ctx.yawRadians));
        return AssemblyResult.SUCCESS;
    }
}
