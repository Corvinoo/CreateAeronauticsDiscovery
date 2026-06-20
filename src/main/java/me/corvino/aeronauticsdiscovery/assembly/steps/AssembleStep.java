package me.corvino.aeronauticsdiscovery.assembly.steps;

import com.simibubi.create.content.contraptions.AssemblyException;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class AssembleStep implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        if (ctx.assemblerPos == null) {
            return AssemblyResult.FAIL;
        }

        BlockPos pos = ctx.assemblerPos;
        var state = ctx.level.getBlockState(pos);
        BlockPos toAssemble = pos;

        if (state.getBlock() instanceof PhysicsAssemblerBlock) {
            Direction stickyFacing = PhysicsAssemblerBlock.getStickyFacing(state);
            toAssemble = pos.relative(stickyFacing);
        }

        SimAssemblyHelper.AssemblyResult result;
        try {
            result = SimAssemblyHelper.assembleFromSingleBlock(
                    ctx.level, pos, toAssemble, true, true
            );
        } catch (AssemblyException e) {
            CreateAeronauticsDiscovery.LOGGER.warn("[ASSEMBLE] AssemblyException for '{}' from {}: {}",
                    ctx.templateId, toAssemble, e.getMessage());
            return AssemblyResult.FAIL;
        }

        if (result == null) {
            CreateAeronauticsDiscovery.LOGGER.warn("[ASSEMBLE] Simulated could not assemble '{}' from {} (selected block: {})",
                    ctx.templateId, toAssemble, ctx.level.getBlockState(toAssemble).getBlock());
            return AssemblyResult.FAIL;
        }

        ctx.assemblyResult = result;
        return AssemblyResult.SUCCESS;
    }
}
