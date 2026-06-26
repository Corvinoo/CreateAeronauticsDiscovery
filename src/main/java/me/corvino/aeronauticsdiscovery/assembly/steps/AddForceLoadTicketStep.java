package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import net.minecraft.util.Unit;

/**
 * This step should be generally required for ALL flyovers since the lifecycle is handled by an external manager and is not using loading lifecycles as world gen ones.
 */
public class AddForceLoadTicketStep implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        assert ctx.assemblyResult != null;
        var container = SubLevelContainer.getContainer(ctx.level);
        assert container != null;
        container.addForceLoadTicket((ServerSubLevel) ctx.assemblyResult.subLevel(), SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        return AssemblyResult.SUCCESS;
    }

    @Override
    public void cleanup(AssemblyContext ctx) {
        if (ctx.assemblyResult == null) return;
        if (!(ctx.assemblyResult.subLevel() instanceof ServerSubLevel serverSubLevel)) return;
        var container = SubLevelContainer.getContainer(ctx.level);
        if (container == null) return;
        container.removeForceLoadTicket(serverSubLevel, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    }
}
