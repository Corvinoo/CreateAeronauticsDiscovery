package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import me.corvino.aeronauticsdiscovery.seat.SeatPopulator;
import net.minecraft.world.entity.Mob;

public class PopulateSeatsStep extends AssemblyStep {
    @Override
    protected void build(Sequence seq) {
        seq.completeIf(ctx -> ctx.assemblyResult == null || ctx.seatsPopulated)
                .run(ctx -> {
                    assert ctx.assemblyResult != null;
                    SeatPopulator.spawnTraders(ctx.assemblyResult.subLevel());
                })
                .delay(1)
                .run(this::forceEntityUpdate)
                .run(ctx -> {
                    assert ctx.assemblyResult != null;
                    SeatPopulator.sitTraders(ctx.assemblyResult.subLevel());
                    ctx.seatsPopulated = true;
                });
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        if (ctx.assemblyResult == null) return;
        if (!(ctx.assemblyResult.subLevel() instanceof ServerSubLevel serverSubLevel)) return;
        FlyoverUtils.removeAllEntitiesInSublevel(serverSubLevel, false, e -> e instanceof Mob, true);
        ctx.seatsPopulated = false;
    }
}