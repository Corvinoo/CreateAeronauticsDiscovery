package me.corvino.aeronauticsdiscovery.assembly.steps;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public class PlaceBlocksStep extends AssemblyStep {
    private boolean placeSucceeded = false;

    @Override
    protected void build(Sequence seq) {
        seq
                .completeIf(ctx -> ctx.assemblerPos != null)
                .run(this::placeStructure)
                .require(ctx -> placeSucceeded, "could not place the structure!")
                .delay(1)
                .run(this::forceEntityUpdate);
    }

    private void placeStructure(AssemblyContext ctx) {
        placeSucceeded = ctx.structureTemplate().placeInWorld(ctx.level, ctx.anchor, ctx.anchor, ctx.defaultPlacementSettings(), ctx.level.getRandom(), 2);
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        placeSucceeded = false;
        removeBlocks(ctx);
    }

    private void removeBlocks(AssemblyContext ctx) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

        var aabb = AABB.of(ctx.templateBounds()).inflate(2.0);

        ctx.level.getEntitiesOfClass(ControlledContraptionEntity.class, aabb).forEach(AbstractContraptionEntity::disassemble);

        for (BlockPos pos : BlockPos.betweenClosed(ctx.templateBounds().minX(), ctx.templateBounds().minY(), ctx.templateBounds().minZ(),
                ctx.templateBounds().maxX(), ctx.templateBounds().maxY(), ctx.templateBounds().maxZ())) {
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

