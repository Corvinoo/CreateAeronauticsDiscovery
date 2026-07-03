package me.corvino.aeronauticsdiscovery.assembly.steps;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

public class PlaceBlocksStep extends AssemblyStep {
    private boolean placeSucceeded = false;

    @Override
    protected void build(Sequence seq) {
        seq
                .completeIf(ctx -> ctx.assemblerPos != null)
                .require(ctx -> ctx.template != null, "template missing")
                .require(ctx -> ctx.anchor != null, "anchor missing")
                .require(ctx -> ctx.level != null, "level is missing")
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
        StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(rot);

        if (ctx.bounds == null) {
            ctx.bounds = ctx.template.getBoundingBox(settings, ctx.anchor);
        }

        placeSucceeded = ctx.template.placeInWorld(ctx.level, ctx.anchor, ctx.anchor, settings, ctx.level.getRandom(), 2);
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        placeSucceeded = false;
        removeBlocks(ctx);
    }

    private void removeBlocks(AssemblyContext ctx) {
        //todo maybe clean up blocks that are tagged in some way only? so it should impossible to remove user buildings
        assert ctx.bounds != null;
        assert ctx.level != null;
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

        var aabb = AABB.of(ctx.bounds).inflate(2.0);

        ctx.level.getEntitiesOfClass(ControlledContraptionEntity.class, aabb).forEach(AbstractContraptionEntity::disassemble);

        for (BlockPos pos : BlockPos.betweenClosed(ctx.bounds.minX(), ctx.bounds.minY(), ctx.bounds.minZ(),
                ctx.bounds.maxX(), ctx.bounds.maxY(), ctx.bounds.maxZ())) {
            var be = ctx.level.getBlockEntity(pos);
            if (be instanceof net.minecraft.world.Container container) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    container.removeItem(slot, container.getItem(slot).getCount());
                }
            }
            ctx.level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
        }

        ctx.level.getEntities(null, aabb).forEach(entity -> {
            if (entity instanceof Player) return;
            entity.remove(Entity.RemovalReason.DISCARDED);
        });
    }
}

