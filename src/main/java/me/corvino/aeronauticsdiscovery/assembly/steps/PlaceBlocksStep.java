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
    private boolean placeSucceeded = false;

    @Override
    protected void build(Sequence seq) {
        seq.completeIf(ctx -> ctx.assemblerPos != null)
                .require(ctx -> ctx.template != null,
                        "template missing")
                .require(ctx -> ctx.anchor != null,
                        "anchor missing")
                .require(ctx -> ctx.level != null,
                        "level is missing")
                .run(this::placeStructure)
                .require(ctx -> placeSucceeded, "could not place the structure!")
                .delay(1)
                .run(this::forceEntityUpdate);
    }

    private void placeStructure(AssemblyContext ctx) {
        assert ctx.template != null;
        assert ctx.anchor != null;
        assert ctx.level != null;
        Rotation rot = ctx.rotationTemplate != null ? ctx.rotationTemplate : Rotation.NONE;
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(rot);

        if (ctx.bounds == null) {
            ctx.bounds = ctx.template.getBoundingBox(settings, ctx.anchor);
        }

        placeSucceeded = ctx.template.placeInWorld(
                ctx.level, ctx.anchor, ctx.anchor, settings, ctx.level.getRandom(), 4);
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        placeSucceeded = false;
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

