package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public class RotateSubLevelStep extends AssemblyStep {
    private RigidBodyHandle cachedHandle;

    @Override
    protected void build(Sequence seq) {
        seq
                .completeIf(ctx-> ctx.yawRadians == 0.0)
                .require(ctx -> ctx.assemblyResult != null,
                        "assembly result missing or something strange happened with the sublevel")
                .waitUntil(this::handleBecomesValid)
                .run(this::rotate)
                .delay(1);
    }

    private boolean handleBecomesValid(AssemblyContext ctx) {
        assert ctx.assemblyResult != null;
        cachedHandle = RigidBodyHandle.of((ServerSubLevel) ctx.assemblyResult.subLevel());
        return cachedHandle.isValid();
    }

    private void rotate(AssemblyContext ctx) {
        Vector3d bodyPos = new Vector3d(
                ctx.templateBounds().minX() + (ctx.templateBounds().maxX() - ctx.templateBounds().minX() + 1) / 2.0,
                ctx.templateBounds().minY() + (ctx.templateBounds().maxY() - ctx.templateBounds().minY() + 1) / 2.0,
                ctx.templateBounds().minZ() + (ctx.templateBounds().maxZ() - ctx.templateBounds().minZ() + 1) / 2.0
        );
        cachedHandle.teleport(bodyPos, new Quaterniond().rotationY(ctx.yawRadians));
    }
}
