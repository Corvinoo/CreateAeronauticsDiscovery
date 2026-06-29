package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class FindAssemblyStartStep extends AssemblyStep {

    @Override
    protected AssemblyResult tick(AssemblyContext ctx) {
        if (ctx.assemblerPos != null) return AssemblyResult.SUCCESS;
        if (ctx.level == null || ctx.bounds == null) return AssemblyResult.FAIL;

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

        if (found != null) {
            ctx.assemblerPos = found;
        } else if (firstNonAir != null) {
            ctx.assemblerPos = firstNonAir;
        } else {
            return AssemblyResult.FAIL;
        }

        return AssemblyResult.SUCCESS;
    }
}