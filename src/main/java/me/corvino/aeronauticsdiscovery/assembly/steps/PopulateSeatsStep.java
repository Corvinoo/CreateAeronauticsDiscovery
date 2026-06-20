package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyStep;
import me.corvino.aeronauticsdiscovery.seat.SeatPopulator;

public class PopulateSeatsStep implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        if (ctx.worldSeatPositions.isEmpty() || ctx.assemblyResult == null || ctx.seatsPopulated) {
            return AssemblyResult.SUCCESS;
        }

        SeatPopulator.populateSeats(ctx.assemblyResult.subLevel(), ctx.worldSeatPositions, ctx.assemblyResult.offset());
        ctx.seatsPopulated = true;
        return AssemblyResult.SUCCESS;
    }
}
