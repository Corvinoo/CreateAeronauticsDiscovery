package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

public class PlaceBlocksStep implements DeferrableStep {

    @Override
    public AssemblyResult begin(AssemblyContext ctx) {
        if (ctx.assemblerPos != null) return AssemblyResult.SUCCESS;
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

        boolean placed = ctx.template.placeInWorld(
                ctx.level, ctx.anchor, ctx.anchor, settings, ctx.level.getRandom(), 2
        );

        if (!placed) return AssemblyResult.FAIL;

        ctx.stepData.put("placeblocks:readyAt", ctx.currentTick + 2);
        return AssemblyResult.WAITING;
    }

    @Override
    public AssemblyResult poll(AssemblyContext ctx) {
        long readyAt = (long) ctx.stepData.getOrDefault("placeblocks:readyAt", 0L);
        if (ctx.currentTick < readyAt) return AssemblyResult.WAITING;
        ctx.stepData.remove("placeblocks:readyAt");
        return AssemblyResult.SUCCESS;
    }

    @Override
    public void abort(AssemblyContext ctx) {
        ctx.stepData.remove("placeblocks:readyAt");
        cleanup(ctx);
    }

    @Override
    public void cleanup(AssemblyContext ctx) {
        if (ctx.bounds == null || ctx.level == null) return;
        for (BlockPos pos : BlockPos.betweenClosed(
                ctx.bounds.minX(), ctx.bounds.minY(), ctx.bounds.minZ(),
                ctx.bounds.maxX(), ctx.bounds.maxY(), ctx.bounds.maxZ())) {
            ctx.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}

