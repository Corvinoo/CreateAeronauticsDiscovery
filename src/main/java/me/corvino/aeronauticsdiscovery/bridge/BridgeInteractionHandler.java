package me.corvino.aeronauticsdiscovery.bridge;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import it.unimi.dsi.fastutil.objects.ObjectList;
import me.corvino.aeronauticsdiscovery.entities.EntityRegistry;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class BridgeInteractionHandler {

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;

        BlockPos clickedPos = event.getPos();

        if (!(level.getBlockEntity(clickedPos) instanceof SmartBlockEntity smartBlockEntity)) return;

        RopeStrandHolderBehavior ropeHolder = smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
        if (ropeHolder == null) return;

        ServerRopeStrand strand = ropeHolder.getAttachedStrand();
        if (strand == null || !strand.isActive()) return;

        ItemStack heldItem = event.getEntity().getItemInHand(event.getHand());
        if (!isSlabItem(heldItem)) return;

        ServerLevel serverLevel = (ServerLevel) level;
        ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate(serverLevel);
        if (ropeManager == null) return;

        int maxPlanks = Math.max(0, strand.getPoints().size() - 2);
        if (maxPlanks <= 0) return;

        BridgePlankManager plankManager = BridgePlankManager.get(serverLevel);
        int plankIndex = plankManager.addPlank(serverLevel, strand.getUUID(), maxPlanks);

        if (plankIndex < 0) {
            if (event.getEntity() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("bridge.aeronauticsdiscovery.full"), true);
            }
            return;
        }

        BridgePlankEntity plankEntity = new BridgePlankEntity(EntityRegistry.BRIDGE_PLANK.get(), level);
        plankEntity.init(strand.getUUID(), plankIndex);

        ObjectList<Vector3d> points = strand.getPoints();
        int segmentIndex = plankIndex + 1;
        Vector3dc p0 = points.get(segmentIndex);
        Vector3dc p1 = points.get(segmentIndex + 1);
        double mx = (p0.x() + p1.x()) / 2.0;
        double my = (p0.y() + p1.y()) / 2.0 + 0.06;
        double mz = (p0.z() + p1.z()) / 2.0;
        plankEntity.setPos(mx, my, mz);

        serverLevel.addFreshEntity(plankEntity);

        level.playSound(null, clickedPos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);

        if (!event.getEntity().hasInfiniteMaterials()) {
            heldItem.shrink(1);
        }

        event.setCanceled(true);
    }

    private static boolean isSlabItem(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return block.builtInRegistryHolder().is(BlockTags.SLABS);
        }
        return false;
    }
}
