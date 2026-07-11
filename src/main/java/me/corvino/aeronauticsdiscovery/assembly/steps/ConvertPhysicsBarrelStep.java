package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.child.ChildRole;
import me.corvino.aeronauticsdiscovery.child.ChildSubLevelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ConvertPhysicsBarrelStep extends AssemblyStep {
    //TODO: this must be generalized into a step capable of spawning any requested (by the template entity) items into physics blocks

    @Override
    protected int timeoutTicks() { return Integer.MAX_VALUE; }

    @Override
    protected void build(Sequence seq) {
        seq.run(this::convertBarrels)
                .delay(1);
    }

    private void convertBarrels(AssemblyContext ctx) {
        assert ctx.assemblyResult != null;
        assert ctx.level != null;

        var subLevel = ctx.assemblyResult.subLevel();
        UUID parentId = subLevel.getUniqueId();
        AABB plotBounds = subLevel.getPlot().getBoundingBox().toAABB();
        BoundingBox box = BoundingBox.fromCorners(
                BlockPos.containing(plotBounds.minX, plotBounds.minY, plotBounds.minZ),
                BlockPos.containing(plotBounds.maxX, plotBounds.maxY, plotBounds.maxZ)
        );

        for (BlockPos tempPos : BlockPos.betweenClosed(
                box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ())) {
            if (ctx.level.getBlockState(tempPos).getBlock() instanceof BarrelBlock) {
                var blockList = List.of(tempPos);
                ServerSubLevel childSubLevel = SubLevelAssemblyHelper.assembleBlocks(ctx.level, tempPos, blockList,
                        Objects.requireNonNull(BoundingBox3i.from(blockList)));

                ChildSubLevelManager.tagAs(childSubLevel, ChildRole.PERSISTENT, parentId);
            }
        }
    }
}
