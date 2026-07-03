package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class FindAssemblyStartStep extends AssemblyStep {
    @Override
    protected void build(Sequence seq) {
        seq.completeIf(ctx -> ctx.assemblerPos != null)
                .run(this::findAssemblerPos)
                .require(ctx -> ctx.assemblerPos != null, "No valid blocks found in bounds!");
    }

    private void findAssemblerPos(AssemblyContext ctx) {
        BlockPos found = null;
        BlockPos firstNonAir = null;

        for (BlockPos pos : BlockPos.betweenClosed(
                ctx.templateBounds().minX(), ctx.templateBounds().minY(), ctx.templateBounds().minZ(),
                ctx.templateBounds().maxX(), ctx.templateBounds().maxY(), ctx.templateBounds().maxZ())) {
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