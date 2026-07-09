package me.corvino.aeronauticsdiscovery.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.corvino.aeronauticsdiscovery.items.ItemRegistry;
import me.corvino.aeronauticsdiscovery.items.MarkerWandItem;
import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import me.corvino.aeronauticsdiscovery.marker.MarkerTrigger;
import me.corvino.aeronauticsdiscovery.marker.behaviour.ConfigField;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehaviorType;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehaviorTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class MarkerWandCommand {
    private static final String TAG_BEHAVIOR_ID = "BehaviorId";
    private static final String TAG_CONFIG = "Config";

    private MarkerWandCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("markerwand")
                .requires(source -> source.hasPermission(0))
                .executes(ctx -> showMainUI(ctx.getSource()))
                .then(Commands.literal("cycle")
                        .executes(ctx -> cycle(ctx.getSource())))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> set(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key"),
                                                StringArgumentType.getString(ctx, "value"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> remove(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))))
                .then(Commands.literal("trigger")
                        .executes(ctx -> showTriggerUI(ctx.getSource()))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .executes(ctx -> toggleTrigger(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "kind")))))
                .then(Commands.literal("emitter")
                        .executes(ctx -> showEmitterUI(ctx.getSource()))
                        .then(Commands.literal("radius")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> setEmitterRadius(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "value")))))
                        .then(Commands.literal("speed")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> setEmitterSpeed(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "value"))))))
        );
    }

    private static int cycle(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        MarkerWandItem.initConfig(stack);
        CompoundTag tag = MarkerWandItem.getDataTag(stack);
        String currentStr = tag.getString(TAG_BEHAVIOR_ID);

        List<MarkerBehaviorType<?>> types = new ArrayList<>(MarkerBehaviorTypes.getAll().values());
        if (types.isEmpty()) {
            source.sendFailure(Component.literal("No marker behavior types registered"));
            return 0;
        }

        int idx = 0;
        if (!currentStr.isEmpty()) {
            ResourceLocation currentId = ResourceLocation.tryParse(currentStr);
            if (currentId != null) {
                for (int i = 0; i < types.size(); i++) {
                    if (types.get(i).id().equals(currentId)) {
                        idx = i;
                        break;
                    }
                }
            }
        }

        int nextIdx = (idx + 1) % types.size();
        MarkerBehaviorType<?> nextType = types.get(nextIdx);
        tag.putString(TAG_BEHAVIOR_ID, nextType.id().toString());
        tag.put(TAG_CONFIG, nextType.defaultConfig());
        MarkerWandItem.setDataTag(stack, tag);

        player.sendSystemMessage(MarkerWandItem.buildConfigUI(stack));
        return 1;
    }

    private static int set(CommandSourceStack source, String key, String value) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        MarkerWandItem.initConfig(stack);
        CompoundTag tag = MarkerWandItem.getDataTag(stack);
        String behaviorIdStr = tag.getString(TAG_BEHAVIOR_ID);
        ResourceLocation behaviorId = ResourceLocation.tryParse(behaviorIdStr);
        if (behaviorId == null) {
            source.sendFailure(Component.literal("No behavior configured - use /markerwand cycle first"));
            return 0;
        }

        MarkerBehaviorType<?> type = MarkerBehaviorTypes.byId(behaviorId);
        if (type == null) {
            source.sendFailure(Component.literal("Unknown behavior: " + behaviorIdStr));
            return 0;
        }

        ConfigField field = null;
        for (ConfigField f : type.configFields()) {
            if (f.key().equals(key)) {
                field = f;
                break;
            }
        }
        if (field == null) {
            source.sendFailure(Component.literal("Unknown parameter '" + key + "' for " + behaviorId.getPath()));
            return 0;
        }

        CompoundTag config = tag.getCompound(TAG_CONFIG);
        try {
            MarkerWandItem.writeConfigValue(config, field, value);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Invalid value for " + field.label() + ": " + e.getMessage()));
            return 0;
        }
        tag.put(TAG_CONFIG, config);
        MarkerWandItem.setDataTag(stack, tag);

        player.sendSystemMessage(MarkerWandItem.buildConfigUI(stack));
        return 1;
    }

    private static int remove(CommandSourceStack source, BlockPos pos) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        Level level = player.level();
        MarkerEntity marker = MarkerWandItem.findMarkerAt(level, pos);
        if (marker == null) {
            source.sendFailure(Component.literal("No marker found at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            return 0;
        }

        marker.discard();
        source.sendSuccess(() -> Component.literal("§8[§6✧§8] §aRemoved marker at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
        return 1;
    }

    private static int showMainUI(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        MarkerWandItem.initConfig(stack);
        player.sendSystemMessage(MarkerWandItem.buildConfigUI(stack));
        return 1;
    }

    private static int showTriggerUI(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        MarkerWandItem.initConfig(stack);
        player.sendSystemMessage(MarkerWandItem.buildTriggerMaskUI(stack));
        return 1;
    }

    private static int toggleTrigger(CommandSourceStack source, String kindName) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        MarkerTrigger.Kind kind;
        try {
            kind = MarkerTrigger.Kind.valueOf(kindName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown trigger kind: " + kindName));
            return 0;
        }

        MarkerWandItem.initConfig(stack);
        MarkerWandItem.toggleTriggerKind(stack, kind);
        player.sendSystemMessage(MarkerWandItem.buildTriggerMaskUI(stack));
        return 1;
    }

    private static int showEmitterUI(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        MarkerWandItem.initConfig(stack);
        player.sendSystemMessage(MarkerWandItem.buildEmitterUI(stack));
        return 1;
    }

    private static int setEmitterRadius(CommandSourceStack source, double radius) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        MarkerWandItem.initConfig(stack);
        MarkerWandItem.setEmitterRadius(stack, radius);
        player.sendSystemMessage(MarkerWandItem.buildEmitterUI(stack));
        return 1;
    }

    private static int setEmitterSpeed(CommandSourceStack source, double speed) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        MarkerWandItem.initConfig(stack);
        MarkerWandItem.setEmitterSpeed(stack, speed);
        player.sendSystemMessage(MarkerWandItem.buildEmitterUI(stack));
        return 1;
    }

    private static Player requirePlayer(CommandSourceStack source) {
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("This command can only be used by a player"));
            return null;
        }
        return player;
    }

    private static boolean validateWand(CommandSourceStack source, ItemStack stack) {
        if (stack.getItem() != ItemRegistry.MARKER_WAND.get()) {
            source.sendFailure(Component.literal("You must hold the Marker Wand in your main hand"));
            return false;
        }
        return true;
    }
}
