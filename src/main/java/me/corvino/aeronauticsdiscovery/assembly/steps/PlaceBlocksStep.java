package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyStep;
import me.corvino.aeronauticsdiscovery.seat.SeatPopulator;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

public class PlaceBlocksStep implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        if (ctx.assemblerPos != null) {
            return AssemblyResult.SUCCESS;
        }

        Rotation rot = ctx.rotationTemplate != null ? ctx.rotationTemplate : Rotation.NONE;
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(rot);

        if (ctx.bounds == null) {
            ctx.bounds = ctx.template.getBoundingBox(settings, ctx.anchor);
        }

        boolean placed = ctx.template.placeInWorld(
                ctx.level, ctx.anchor, ctx.anchor, settings, ctx.level.getRandom(), 2
        );

        if (!placed) {
            return AssemblyResult.FAIL;
        }

        ctx.worldSeatPositions.clear();
        ctx.worldSeatPositions.addAll(SeatPopulator.findSeatPositions(ctx.level, ctx.bounds));

        return AssemblyResult.SUCCESS;
    }
}
