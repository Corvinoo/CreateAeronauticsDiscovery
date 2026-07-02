package me.corvino.aeronauticsdiscovery.assembly;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.steps.AssemblyStep;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import net.minecraft.world.entity.item.ItemEntity;

public class CleanUpItemEntities extends AssemblyStep {

    @Override
    protected void build(Sequence seq) {
        seq
                .require(ctx -> ctx.assemblyResult != null, "Assembly result was null!")
                .run(ctx-> {
                    assert ctx.assemblyResult != null;
                    var serverSubLevel = ctx.assemblyResult.subLevel();
                    FlyoverUtils.removeAllEntitiesInSublevel(
                            (ServerSubLevel) serverSubLevel, true, entity -> entity instanceof ItemEntity, false);
                });
    }
}