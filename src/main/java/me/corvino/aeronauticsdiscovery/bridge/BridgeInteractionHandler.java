package me.corvino.aeronauticsdiscovery.bridge;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import me.corvino.aeronauticsdiscovery.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import javax.annotation.Nullable;


public class BridgeInteractionHandler {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aeronauticsdiscovery.BridgeInteractionHandler");

    private static final float PLACE_SOUND_VOLUME = 0.8F;

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos clickedPos = event.getPos();

        if (!(level.getBlockEntity(clickedPos) instanceof SmartBlockEntity smartBlockEntity)) {
            return;
        }

        RopeStrandHolderBehavior ropeHolder = smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
        if (ropeHolder == null) {
            return;
        }

        ItemStack heldItem = event.getEntity().getItemInHand(event.getHand());

        if (level.isClientSide) {
            if (isSlabItem(heldItem)) {
                event.setCanceled(true);
            }
            return;
        }

        ServerRopeStrand strand = ropeHolder.getAttachedStrand();
        if (strand == null || !strand.isActive()) {
            LOG.trace("No active strand at {}", clickedPos);
            return;
        }

        if (!isSlabItem(heldItem)) {
            LOG.trace("Held item not a slab: {}", heldItem);
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        var ropeManager = ServerLevelRopeManager.getOrCreate(serverLevel);
        if (ropeManager == null) {
            LOG.debug("No rope manager");
            return;
        }

        var points = strand.getPoints();
        int maxPlanks = BridgePlankManager.computeMaxPlanks(points);
        if (maxPlanks <= 0) {
            LOG.debug("Not enough points ({}), skipping", points.size());
            return;
        }

        LOG.debug("Strand has {} points, maxPlanks={}", points.size(), maxPlanks);

        boolean clickedEnd = false;
        var startAttach = strand.getAttachment(RopeAttachmentPoint.START);
        if (startAttach == null || !startAttach.blockAttachment().equals(clickedPos)) {
            var endAttach = strand.getAttachment(RopeAttachmentPoint.END);
            if (endAttach != null && endAttach.blockAttachment().equals(clickedPos)) {
                clickedEnd = true;
            }
        }

        int plankIndex = clickedEnd ? maxPlanks - 1 : 0;
        var manager = BridgePlankManager.get(serverLevel);
        var existing = manager.getPlanks(strand.getUUID());
        if (existing != null) {
            var used = new java.util.BitSet();
            for (var info : existing) used.set(info.plankIndex());
            if (clickedEnd) {
                while (plankIndex >= 0 && used.get(plankIndex)) plankIndex--;
            } else {
                while (plankIndex < maxPlanks && used.get(plankIndex)) plankIndex++;
            }
        }
        if (plankIndex >= maxPlanks || plankIndex < 0) {
            if (event.getEntity() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("bridge.aeronauticsdiscovery.full"), true);
            }
            return;
        }

        BlockState slabState = getSlabState(heldItem);
        var subLevel = createPlank(serverLevel, strand, plankIndex, slabState);
        if (subLevel == null) return;

        if (event.getEntity() instanceof ServerPlayer sp) {
            subLevel.getTrackingPlayers().add(sp.getGameProfile().getId());
        }

        level.playSound(null, clickedPos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, PLACE_SOUND_VOLUME, 1.0F);
        LOG.debug("Placement complete for plankIndex={}", plankIndex);

        if (!event.getEntity().hasInfiniteMaterials()) {
            heldItem.shrink(1);
        }

        event.setCanceled(true);
    }

    private static BlockState getSlabState(ItemStack stack) {
        BlockState state = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
        if (state.hasProperty(SlabBlock.TYPE)) {
            state = state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        }
        return state;
    }

    @Nullable
    public static ServerSubLevel createPlank(ServerLevel serverLevel, ServerRopeStrand strand, int plankIndex, BlockState slabState) {
        var points = strand.getPoints();
        var pos = BridgePlankManager.computePlankPosition(points, plankIndex, strand.getCollisionRadius());
        double mx = pos[0], my = pos[1], mz = pos[2];

        var container = (ServerSubLevelContainer) SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            LOG.info("No SubLevelContainer");
            return null;
        }

        var subLevel = (ServerSubLevel) container.allocateNewSubLevel(new dev.ryanhcode.sable.companion.math.Pose3d());
        subLevel.setName("bridge_plank_" + plankIndex);
        LOG.debug("Created subLevel {} name={}", subLevel.getUniqueId(), subLevel.getName());

        var plot = (ServerLevelPlot) subLevel.getPlot();
        var centerChunk = plot.getCenterChunk();
        plot.newEmptyChunk(centerChunk);
        LOG.debug("Created chunk {} in subLevel {}", centerChunk, subLevel.getUniqueId());

        var center = plot.getCenterBlock();
        var accessor = plot.getEmbeddedLevelAccessor();
        accessor.setBlock(BlockPos.ZERO, slabState, 3);
        LOG.debug("Placed slab {} at ZERO", slabState);

        ChunkPos centerChunkPos = new ChunkPos(center);
        PlotChunkHolder holder = plot.getChunkHolder(plot.toLocal(centerChunkPos));
        if (holder != null) {
            int localX = center.getX() & 15;
            int localZ = center.getZ() & 15;
            holder.handleBlockChange(localX, center.getY(), localZ, Blocks.AIR.defaultBlockState(), slabState);
            LOG.debug("Updated chunk holder bounding box via handleBlockChange at local ({},{},{})", localX, center.getY(), localZ);
        }
        plot.updateBoundingBox();

        LOG.debug("Midpoint=({},{},{})", mx, my, mz);

        if (Config.planksLevelled) {
            int segIdx = (int) pos[3];
            BridgeUtility.setYawOrientation(subLevel.logicalPose().orientation(), points.get(segIdx), points.get(segIdx + 1));
        }
        subLevel.logicalPose().position().set(mx, my, mz);
        subLevel.updateLastPose();

        var pipeline = container.physicsSystem().getPipeline();
        pipeline.teleport(subLevel,
                subLevel.logicalPose().position(),
                subLevel.logicalPose().orientation());
        pipeline.resetVelocity(subLevel);
        LOG.debug("Teleported subLevel to ({},{},{})", mx, my, mz);

        var manager = BridgePlankManager.get(serverLevel);
        manager.addPlank(strand.getUUID(), subLevel.getUniqueId(), plankIndex, slabState);
        LOG.debug("Registered plank: rope={} seg={} subLevel={}", strand.getUUID(), plankIndex, subLevel.getUniqueId());

        return subLevel;
    }

    private static boolean isSlabItem(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return block.builtInRegistryHolder().is(BlockTags.SLABS);
        }
        return false;
    }
}
