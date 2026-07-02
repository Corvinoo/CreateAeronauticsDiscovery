package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;

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

            if (!ctx.template.placeInWorld(ctx.level, ctx.anchor, ctx.anchor, settings, ctx.level.getRandom(), 4))
                return AssemblyResult.FAIL;

            placed = true;
        }

        postPlaceDelay.start(1); // delays this since the template can be pretty big and potentially strain the server
        if (postPlaceDelay.isWaiting()) return AssemblyResult.WAITING;

        this.forceEntityUpdate(ctx);

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


