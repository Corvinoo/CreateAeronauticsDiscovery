package me.corvino.aeronauticsdiscovery.items;

import me.corvino.aeronauticsdiscovery.glue.GlueHierarchy;
import me.corvino.aeronauticsdiscovery.glue.KelpGlueEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class KelpGlueItem extends Item {

    public static final int MAX_SIZE = 64;
    private static final String TAG_CORNER = "GlueCorner";

    public KelpGlueItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!hasOperatorPermission(player)) {
            notify(player, Component.literal("Requires permission level 2").withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }
        if (player.isShiftKeyDown() && getFirstCorner(stack) != null) {
            clearFirstCorner(stack);
            notify(player, Component.literal("Selection cleared").withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;
        if (!hasOperatorPermission(player)) {
            notify(player, Component.literal("Requires permission level 2").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        BlockPos pos = context.getClickedPos();

        if (player.isShiftKeyDown()) {
            if (getFirstCorner(stack) != null) {
                clearFirstCorner(stack);
                notify(player, Component.literal("Selection cleared").withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.CONSUME;
        }

        BlockPos first = getFirstCorner(stack);
        if (first == null) {
            setFirstCorner(stack, pos);
            notify(player, Component.literal("First corner set").withStyle(ChatFormatting.AQUA));
            return InteractionResult.CONSUME;
        }

        BoundingBox box = BoundingBox.fromCorners(first, pos);
        if (box.getXSpan() > MAX_SIZE || box.getYSpan() > MAX_SIZE || box.getZSpan() > MAX_SIZE) {
            notify(player, Component.literal("Selection too large (max " + MAX_SIZE + " per axis)").withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }

        Optional<KelpGlueEntity> conflict = gluesNear(level, box).stream()
                .filter(glue -> GlueHierarchy.conflicts(glue.blockBox(), box))
                .findFirst();
        if (conflict.isPresent()) {
            notify(player, Component.literal("Selection overlaps another glue box without being fully contained")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }

        level.addFreshEntity(KelpGlueEntity.create(level, box));
        clearFirstCorner(stack);
        notify(player, Component.literal("Glued").withStyle(ChatFormatting.AQUA));
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!event.getItemStack().is(ItemRegistry.KELP_GLUE.get())) return;
        event.setCanceled(true);
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (!hasOperatorPermission(player)) return;
        KelpGlueEntity target = hoveredGlue(player);
        if (target == null) return;
        target.discard();
        notify(player, Component.literal("Removed glue").withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    public static KelpGlueEntity hoveredGlue(Player player) {
        double range = player.blockInteractionRange() + 1.0;
        Vec3 origin = player.getEyePosition();
        Vec3 target = origin.add(player.getViewVector(1.0F).scale(range));
        KelpGlueEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        long bestVolume = Long.MAX_VALUE;
        for (KelpGlueEntity glue : player.level().getEntitiesOfClass(KelpGlueEntity.class, player.getBoundingBox().inflate(range))) {
            AABB box = AABB.of(glue.blockBox());
            double distance;
            if (box.contains(origin)) {
                distance = 0.0;
            } else {
                Optional<Vec3> clip = box.clip(origin, target);
                if (clip.isEmpty()) continue;
                distance = clip.get().distanceToSqr(origin);
            }
            long volume = GlueHierarchy.volume(glue.blockBox());
            if (distance < bestDistance || (distance == bestDistance && volume < bestVolume)) {
                best = glue;
                bestDistance = distance;
                bestVolume = volume;
            }
        }
        return best;
    }

    public static List<KelpGlueEntity> gluesNear(Level level, BoundingBox box) {
        return level.getEntitiesOfClass(KelpGlueEntity.class, AABB.of(box).inflate(MAX_SIZE));
    }

    public static boolean isHolding(Player player) {
        Item item = ItemRegistry.KELP_GLUE.get();
        return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
    }

    @Nullable
    public static BlockPos getFirstCorner(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(TAG_CORNER)) return null;
        return BlockPos.of(tag.getLong(TAG_CORNER));
    }

    private static void setFirstCorner(ItemStack stack, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(TAG_CORNER, pos.asLong()));
    }

    private static void clearFirstCorner(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(TAG_CORNER));
    }

    private static void notify(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    private static boolean hasOperatorPermission(Player player) {
        return player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2);
    }
}
