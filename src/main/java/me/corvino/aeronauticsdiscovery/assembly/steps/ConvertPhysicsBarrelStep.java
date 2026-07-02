package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Objects;

public class ConvertPhysicsBarrelStep extends AssemblyStep {
    //TODO: this must be generalized into a step capable of spawning any requested (by the template entity) items into physics blocks
    private final Guard barrelsPlaced = newGuard();
    private final TickDelay postPlaceDelay = newDelay();

    @Override
    protected int timeoutTicks() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected AssemblyResult tick(AssemblyContext ctx) {
        assert ctx.level != null;
        assert ctx.bounds != null;
        var bounds = ctx.bounds;

        if (!barrelsPlaced.isSet()) {
            assert ctx.assemblyResult != null;
            var subLevel = ctx.assemblyResult.subLevel();

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
                    SubLevelAssemblyHelper.assembleBlocks(ctx.level, tempPos, blockList,
                            Objects.requireNonNull(BoundingBox3i.from(blockList)));
                }
            }
            barrelsPlaced.set();
            postPlaceDelay.start(1);
        }
        if (postPlaceDelay.isWaiting()) return AssemblyResult.WAITING;

        return AssemblyResult.SUCCESS;
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {

    }
}
