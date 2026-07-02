package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public class RotateSubLevelStep extends AssemblyStep {
    private RigidBodyHandle cachedHandle;

    @Override
    protected void build(Sequence seq) {
        seq.completeIf(ctx -> ctx.yawRadians == 0.0 || ctx.bounds == null)
                .require(ctx -> ctx.assemblyResult != null
                                && ctx.assemblyResult.subLevel() instanceof ServerSubLevel,
                        "assembly result missing or something strange happened with the sublevel")
                .waitUntil(this::handleBecomesValid)
                .run(this::rotate);
    }

    private boolean handleBecomesValid(AssemblyContext ctx) {
        assert ctx.assemblyResult != null;
        cachedHandle = RigidBodyHandle.of((ServerSubLevel) ctx.assemblyResult.subLevel());
        return cachedHandle.isValid();
    }

    private void rotate(AssemblyContext ctx) {
        Vector3d bodyPos = new Vector3d(
                ctx.bounds.minX() + (ctx.bounds.maxX() - ctx.bounds.minX() + 1) / 2.0,
                ctx.bounds.minY() + (ctx.bounds.maxY() - ctx.bounds.minY() + 1) / 2.0,
                ctx.bounds.minZ() + (ctx.bounds.maxZ() - ctx.bounds.minZ() + 1) / 2.0
        );
        cachedHandle.teleport(bodyPos, new Quaterniond().rotationY(ctx.yawRadians));
    }
}
