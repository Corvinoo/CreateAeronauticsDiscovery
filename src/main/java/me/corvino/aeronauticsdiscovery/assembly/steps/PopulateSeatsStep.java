package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import me.corvino.aeronauticsdiscovery.seat.SeatPopulator;
import net.minecraft.world.entity.Mob;

public class PopulateSeatsStep extends AssemblyStep {
    private final Guard entitiesSpawned = newGuard();
    private final TickDelay settleDelay = newDelay();

    @Override
    protected AssemblyResult tick(AssemblyContext ctx) {
        if (ctx.assemblyResult == null || ctx.seatsPopulated) return AssemblyResult.SUCCESS;

        if (!entitiesSpawned.isSet()) {
            SeatPopulator.spawnTraders(ctx.assemblyResult.subLevel());
            entitiesSpawned.set();
        }

        settleDelay.start(1);
        if (settleDelay.isWaiting()) return AssemblyResult.WAITING;
        this.forceEntityUpdate(ctx);

        SeatPopulator.sitTraders(ctx.assemblyResult.subLevel());
        ctx.seatsPopulated = true;
        return AssemblyResult.SUCCESS;
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        if (ctx.assemblyResult == null) return;
        if (!(ctx.assemblyResult.subLevel() instanceof ServerSubLevel serverSubLevel)) return;
        FlyoverUtils.removeAllEntitiesInSublevel(serverSubLevel, false, e -> e instanceof Mob, true);
        ctx.seatsPopulated = false;
    }
}