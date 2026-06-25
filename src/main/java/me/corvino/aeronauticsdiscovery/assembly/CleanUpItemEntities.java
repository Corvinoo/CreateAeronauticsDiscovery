package me.corvino.aeronauticsdiscovery.assembly;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.steps.AssemblyStep;
import me.corvino.aeronauticsdiscovery.event.FlyoverManager;
import net.minecraft.world.entity.item.ItemEntity;

public class CleanUpItemEntities implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        assert ctx.assemblyResult != null;
        if(ctx.assemblyResult.subLevel() == null) return AssemblyResult.FAIL;
        FlyoverManager.removeAllEntitiesInSublevel((ServerSubLevel)ctx.assemblyResult.subLevel(), true, entity -> entity instanceof ItemEntity);
        return AssemblyResult.SUCCESS;
    }
}
