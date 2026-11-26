package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.glue.GlueSelection;
import me.corvino.aeronauticsdiscovery.glue.KelpGlueEntity;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.PIPELINE;

public class CollectGlueSelectionsStep extends AssemblyStep {

    @Override
    protected void build(Sequence seq) {
        seq.run(this::collect);
    }

    private void collect(AssemblyContext ctx) {
        AABB search = AABB.of(ctx.templateBounds()).inflate(1.0);
        List<GlueSelection> selections = new ArrayList<>();
        for (KelpGlueEntity glue : ctx.level.getEntitiesOfClass(KelpGlueEntity.class, search)) {
            selections.add(new GlueSelection(glue.getUUID(), glue.blockBox()));
        }
        ctx.glueSelections = selections;
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        ctx.glueSelections = new ArrayList<>();
    }
}
