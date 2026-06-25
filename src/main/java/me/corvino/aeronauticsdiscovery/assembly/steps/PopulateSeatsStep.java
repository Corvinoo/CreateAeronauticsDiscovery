package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyStep;
import me.corvino.aeronauticsdiscovery.entities.SoaringTrader;
import me.corvino.aeronauticsdiscovery.event.FlyoverManager;
import me.corvino.aeronauticsdiscovery.seat.SeatPopulator;
import net.minecraft.world.entity.Mob;

public class PopulateSeatsStep implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        if (ctx.assemblyResult == null || ctx.seatsPopulated) {
            return AssemblyResult.SUCCESS;
        }

        SeatPopulator.populateSeats(ctx.assemblyResult.subLevel());
        ctx.seatsPopulated = true;
        return AssemblyResult.SUCCESS;
    }


    @Override
    public void cleanup(AssemblyContext ctx) {
        if (ctx.assemblyResult == null) return;
        if (!(ctx.assemblyResult.subLevel() instanceof ServerSubLevel serverSubLevel)) return;
        FlyoverManager.removeAllEntitiesInSublevel(serverSubLevel, false,
                e -> e instanceof Mob);
        ctx.seatsPopulated = false;
    }

}
