package me.corvino.aeronauticsdiscovery.assembly;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.steps.AssemblyStep;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import net.minecraft.world.entity.item.ItemEntity;

public class CleanUpItemEntities extends AssemblyStep {

    @Override
    protected AssemblyResult tick(AssemblyContext ctx) {
        if (ctx.assemblyResult == null) return AssemblyResult.FAIL;
        if (!(ctx.assemblyResult.subLevel() instanceof ServerSubLevel serverSubLevel))
            return AssemblyResult.FAIL;

        FlyoverUtils.removeAllEntitiesInSublevel(
                serverSubLevel, true, entity -> entity instanceof ItemEntity, false);
        return AssemblyResult.SUCCESS;
    }
}