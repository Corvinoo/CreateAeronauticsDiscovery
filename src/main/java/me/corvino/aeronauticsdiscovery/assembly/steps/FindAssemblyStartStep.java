package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class FindAssemblyStartStep extends AssemblyStep {
    @Override
    protected void build(Sequence seq) {
        seq.completeIf(ctx -> ctx.assemblerPos != null)
                .require(ctx -> ctx.level != null, "level is somehow missing")
                .require(ctx -> ctx.bounds != null, "bounds is somehow missing")
                .run(this::findAssemblerPos)
                .require(ctx -> ctx.assemblerPos != null, "No valid blocks found in bounds!");
    }

    private void findAssemblerPos(AssemblyContext ctx) {
        assert ctx.level != null;
        assert ctx.bounds != null;
        BlockPos found = null;
        BlockPos firstNonAir = null;

        for (BlockPos pos : BlockPos.betweenClosed(
                ctx.bounds.minX(), ctx.bounds.minY(), ctx.bounds.minZ(),
                ctx.bounds.maxX(), ctx.bounds.maxY(), ctx.bounds.maxZ())) {
            BlockPos immutable = pos.immutable();
            var state = ctx.level.getBlockState(immutable);

            if (state.isAir()) continue;
            if (firstNonAir == null) firstNonAir = immutable;

            if (state.getBlock() instanceof PhysicsAssemblerBlock) {
                Direction stickyFacing = PhysicsAssemblerBlock.getStickyFacing(state);
                BlockPos toAssemble = immutable.relative(stickyFacing);
                if (!ctx.level.getBlockState(toAssemble).isAir()) {
                    found = immutable;
                    break;
                }
            }
        }

        ctx.assemblerPos = found != null ? found : firstNonAir;
    }
}