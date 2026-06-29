package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

public class PlaceBlocksStep extends AssemblyStep {
    private final TickDelay postPlaceDelay = newDelay();
    private boolean placed = false;

    @Override
    protected AssemblyResult tick(AssemblyContext ctx) {
        if (ctx.assemblerPos != null) return AssemblyResult.SUCCESS;
        if (ctx.template == null || ctx.anchor == null || ctx.level == null) return AssemblyResult.FAIL;

        if (!placed) {
            Rotation rot = ctx.rotationTemplate != null ? ctx.rotationTemplate : Rotation.NONE;
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setMirror(Mirror.NONE)
                    .setRotation(rot);

            if (ctx.bounds == null)
                ctx.bounds = ctx.template.getBoundingBox(settings, ctx.anchor);

            if (!ctx.template.placeInWorld(ctx.level, ctx.anchor, ctx.anchor, settings, ctx.level.getRandom(), 2))
                return AssemblyResult.FAIL;

            placed = true;
        }

        postPlaceDelay.start(2);
        if (postPlaceDelay.isWaiting()) return AssemblyResult.WAITING;
        return AssemblyResult.SUCCESS;
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        placed = false;
        removeBlocks(ctx);
    }

    private void removeBlocks(AssemblyContext ctx) {
        if (ctx.bounds == null || ctx.level == null) return;
        for (BlockPos pos : BlockPos.betweenClosed(
                ctx.bounds.minX(), ctx.bounds.minY(), ctx.bounds.minZ(),
                ctx.bounds.maxX(), ctx.bounds.maxY(), ctx.bounds.maxZ()))
            ctx.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }
}
