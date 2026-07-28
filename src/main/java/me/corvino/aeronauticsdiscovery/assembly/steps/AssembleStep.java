package me.corvino.aeronauticsdiscovery.assembly.steps;

import com.simibubi.create.content.contraptions.AssemblyException;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.PIPELINE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;

public class AssembleStep extends AssemblyStep {
    @Override
    protected int timeoutTicks() { return Integer.MAX_VALUE; }

    @Override
    protected void build(Sequence seq) {
        seq.run(this::doAssemble)
                .require(ctx -> ctx.assemblyResult != null, "Assembly failed or got AssemblyException");
    }

    private void doAssemble(AssemblyContext ctx) {
        assert ctx.level != null;
        BlockPos pos = ctx.assemblerPos;
        assert pos != null;
        var blockState = ctx.level.getBlockState(pos);
        BlockPos toAssemble = pos;
        if (blockState.getBlock() instanceof PhysicsAssemblerBlock) {
            Direction stickyFacing = PhysicsAssemblerBlock.getStickyFacing(blockState);
            toAssemble = pos.relative(stickyFacing);
        }

        SimAssemblyHelper.AssemblyResult result;
        try {
            result = SimAssemblyHelper.assembleFromSingleBlock(ctx.level, pos, toAssemble, true, true);
        } catch (AssemblyException e) {
            ModLog.warn(PIPELINE,
                    "AssemblyException for '{}' at pos {}: {}",
                    ctx.templateId, toAssemble, e.getMessage());
            return;
        }

        if (result == null) {
            ModLog.warn(PIPELINE,
                    "Simulated could not assemble '{}' at {}", ctx.templateId, toAssemble);
            return;
        }

        //TODO: better entity tagging
        var plotAABB = result.subLevel().getPlot().getBoundingBox().toAABB();
        ctx.level.getEntities((Entity) null, plotAABB, e -> !(e instanceof ServerPlayer))
                .forEach(e -> e.getPersistentData().putUUID(SUBLEVEL_ID_TAG, result.subLevel().getUniqueId()));

        ctx.assemblyResult = result;
        ctx.subLevelId = result.subLevel().getUniqueId();
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        if (ctx.assemblyResult == null) return;
        SubLevel subLevel = ctx.assemblyResult.subLevel();
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) return;
        FlyoverUtils.removeAllEntitiesInSublevel(serverSubLevel, false);
        SubLevelContainer container = SubLevelContainer.getContainer(ctx.level);
        if (container != null) container.removeSubLevel(serverSubLevel, SubLevelRemovalReason.REMOVED);
        ctx.assemblyResult = null;
    }
}

