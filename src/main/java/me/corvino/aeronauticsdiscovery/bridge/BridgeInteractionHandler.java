package me.corvino.aeronauticsdiscovery.bridge;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
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

public class BridgeInteractionHandler {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aeronauticsdiscovery.BridgeInteractionHandler");

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;

        BlockPos clickedPos = event.getPos();
        LOG.info("RightClickBlock at {}", clickedPos);

        if (!(level.getBlockEntity(clickedPos) instanceof SmartBlockEntity smartBlockEntity)) {
            LOG.info("No SmartBlockEntity at {}", clickedPos);
            return;
        }

        RopeStrandHolderBehavior ropeHolder = smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
        if (ropeHolder == null) {
            LOG.info("No RopeStrandHolderBehavior at {}", clickedPos);
            return;
        }

        ServerRopeStrand strand = ropeHolder.getAttachedStrand();
        if (strand == null || !strand.isActive()) {
            LOG.info("No active strand at {}", clickedPos);
            return;
        }

        ItemStack heldItem = event.getEntity().getItemInHand(event.getHand());
        if (!isSlabItem(heldItem)) {
            LOG.info("Held item not a slab: {}", heldItem);
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        var ropeManager = ServerLevelRopeManager.getOrCreate(serverLevel);
        if (ropeManager == null) {
            LOG.info("No rope manager");
            return;
        }

        var points = strand.getPoints();
        int maxPlanks = Math.max(0, points.size() - 2);
        if (maxPlanks <= 0) {
            LOG.info("Not enough points ({}), skipping", points.size());
            return;
        }

        LOG.info("Strand has {} points, maxPlanks={}", points.size(), maxPlanks);

        // Find first empty segment
        int plankIndex = 0;
        var manager = BridgePlankManager.get(serverLevel);
        var existing = manager.getPlanks(strand.getUUID());
        if (existing != null) {
            var used = new java.util.BitSet();
            for (var info : existing) used.set(info.segmentIndex());
            while (plankIndex < maxPlanks && used.get(plankIndex)) plankIndex++;
        }
        if (plankIndex >= maxPlanks) {
            if (event.getEntity() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("bridge.aeronauticsdiscovery.full"), true);
            }
            return;
        }

        // Compute slab position from rope segment (segIdx skips the two anchor points)
        int segIdx = plankIndex + 1;
        var p0 = points.get(segIdx);
        var p1 = points.get(segIdx + 1);
        double mx = (p0.x() + p1.x()) / 2.0;
        double my = (p0.y() + p1.y()) / 2.0 + 0.03;
        double mz = (p0.z() + p1.z()) / 2.0;

        var container = (ServerSubLevelContainer) SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            LOG.info("No SubLevelContainer");
            return;
        }

        var subLevel = (ServerSubLevel) container.allocateNewSubLevel(new dev.ryanhcode.sable.companion.math.Pose3d());
        subLevel.setName("bridge_plank_" + plankIndex);
        LOG.debug("Created subLevel {} name={}", subLevel.getUniqueId(), subLevel.getName());

        // Add chunk and place slab at local origin
        BlockState slabState = getSlabState(heldItem);
        var plot = (ServerLevelPlot) subLevel.getPlot();
        var centerChunk = plot.getCenterChunk();
        plot.newEmptyChunk(centerChunk);
        LOG.debug("Created chunk {} in subLevel {}", centerChunk, subLevel.getUniqueId());

        var center = plot.getCenterBlock();
        var accessor = plot.getEmbeddedLevelAccessor();
        accessor.setBlock(BlockPos.ZERO, slabState, 3);
        BlockState placed = accessor.getBlockState(BlockPos.ZERO);
        LOG.debug("Placed slab {} at ZERO, readback={}", slabState, placed);

        // center is ONLY used here, to locate the chunk holder in the plot's own local storage space so its bounding box gets updated; it must never be used for pose math
        ChunkPos centerChunkPos = new ChunkPos(center);
        PlotChunkHolder holder = plot.getChunkHolder(plot.toLocal(centerChunkPos));
        if (holder != null) {
            int localX = center.getX() & 15;
            int localZ = center.getZ() & 15;
            holder.handleBlockChange(localX, center.getY(), localZ, Blocks.AIR.defaultBlockState(), slabState);
            LOG.debug("Updated chunk holder bounding box via handleBlockChange at local ({},{},{})", localX, center.getY(), localZ);
        }
        plot.updateBoundingBox();

        // Pose is the world position of local origin, that's the rope midpoint itself, no offset by centerBlock
        LOG.debug("Midpoint=({},{},{})", mx, my, mz);
        subLevel.logicalPose().position().set(mx, my, mz);
        subLevel.updateLastPose();

        var pipeline = container.physicsSystem().getPipeline();
        pipeline.teleport(subLevel,
                subLevel.logicalPose().position(),
                subLevel.logicalPose().orientation());
        LOG.debug("Teleported subLevel to ({},{},{})", mx, my, mz);

        if (event.getEntity() instanceof ServerPlayer sp) {
            subLevel.getTrackingPlayers().add(sp.getGameProfile().getId());
        }

        manager.addPlank(strand.getUUID(), subLevel.getUniqueId(), plankIndex, slabState);
        LOG.debug("Registered plank: rope={} seg={} subLevel={}", strand.getUUID(), plankIndex, subLevel.getUniqueId());

        container.addForceLoadTicket(subLevel,
                dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,
                net.minecraft.util.Unit.INSTANCE);
        LOG.debug("Added force-load ticket");

        level.playSound(null, clickedPos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
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

    private static boolean isSlabItem(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return block.builtInRegistryHolder().is(BlockTags.SLABS);
        }
        return false;
    }
}
