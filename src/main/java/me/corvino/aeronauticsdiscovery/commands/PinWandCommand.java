package me.corvino.aeronauticsdiscovery.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.corvino.aeronauticsdiscovery.items.ItemRegistry;
import me.corvino.aeronauticsdiscovery.items.PinWandItem;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
import me.corvino.aeronauticsdiscovery.pin.behaviour.ConfigField;
import me.corvino.aeronauticsdiscovery.pin.behaviour.PinBehaviorType;
import me.corvino.aeronauticsdiscovery.pin.behaviour.PinBehaviorTypes;
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

public final class PinWandCommand {
    private static final String TAG_BEHAVIOR_ID = "BehaviorId";
    private static final String TAG_CONFIG = "Config";

    private PinWandCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pinwand")
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

        PinWandItem.initConfig(stack);
        CompoundTag tag = PinWandItem.getDataTag(stack);
        String currentStr = tag.getString(TAG_BEHAVIOR_ID);

        List<PinBehaviorType<?>> types = new ArrayList<>(PinBehaviorTypes.getAll().values());
        if (types.isEmpty()) {
            source.sendFailure(Component.literal("No pin behavior types registered"));
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
        PinBehaviorType<?> nextType = types.get(nextIdx);
        tag.putString(TAG_BEHAVIOR_ID, nextType.id().toString());
        tag.put(TAG_CONFIG, nextType.defaultConfig());
        PinWandItem.setDataTag(stack, tag);

        player.sendSystemMessage(PinWandItem.buildConfigUI(stack));
        return 1;
    }

    private static int set(CommandSourceStack source, String key, String value) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        PinWandItem.initConfig(stack);
        CompoundTag tag = PinWandItem.getDataTag(stack);
        String behaviorIdStr = tag.getString(TAG_BEHAVIOR_ID);
        ResourceLocation behaviorId = ResourceLocation.tryParse(behaviorIdStr);
        if (behaviorId == null) {
            source.sendFailure(Component.literal("No behavior configured - use /pinwand cycle first"));
            return 0;
        }

        PinBehaviorType<?> type = PinBehaviorTypes.byId(behaviorId);
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
            PinWandItem.writeConfigValue(config, field, value);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Invalid value for " + field.label() + ": " + e.getMessage()));
            return 0;
        }
        tag.put(TAG_CONFIG, config);
        PinWandItem.setDataTag(stack, tag);

        player.sendSystemMessage(PinWandItem.buildConfigUI(stack));
        return 1;
    }

    private static int remove(CommandSourceStack source, BlockPos pos) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        Level level = player.level();
        PinEntity pin = PinWandItem.findPinAt(level, pos);
        if (pin == null) {
            source.sendFailure(Component.literal("No pin found at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            return 0;
        }

        pin.discard();
        source.sendSuccess(() -> Component.literal("§8[§6✧§8] §aRemoved pin at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
        return 1;
    }

    private static int showMainUI(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        PinWandItem.initConfig(stack);
        player.sendSystemMessage(PinWandItem.buildConfigUI(stack));
        return 1;
    }

    private static int showTriggerUI(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        PinWandItem.initConfig(stack);
        player.sendSystemMessage(PinWandItem.buildTriggerMaskUI(stack));
        return 1;
    }

    private static int toggleTrigger(CommandSourceStack source, String kindName) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        PinTrigger.Kind kind;
        try {
            kind = PinTrigger.Kind.valueOf(kindName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown trigger kind: " + kindName));
            return 0;
        }

        PinWandItem.initConfig(stack);
        PinWandItem.toggleTriggerKind(stack, kind);
        player.sendSystemMessage(PinWandItem.buildTriggerMaskUI(stack));
        return 1;
    }

    private static int showEmitterUI(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        PinWandItem.initConfig(stack);
        player.sendSystemMessage(PinWandItem.buildEmitterUI(stack));
        return 1;
    }

    private static int setEmitterRadius(CommandSourceStack source, double radius) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        PinWandItem.initConfig(stack);
        PinWandItem.setEmitterRadius(stack, radius);
        player.sendSystemMessage(PinWandItem.buildEmitterUI(stack));
        return 1;
    }

    private static int setEmitterSpeed(CommandSourceStack source, double speed) {
        Player player = requirePlayer(source);
        if (player == null) return 0;

        ItemStack stack = player.getMainHandItem();
        if (!validateWand(source, stack)) return 0;

        PinWandItem.initConfig(stack);
        PinWandItem.setEmitterSpeed(stack, speed);
        player.sendSystemMessage(PinWandItem.buildEmitterUI(stack));
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
        if (stack.getItem() != ItemRegistry.PIN_WAND.get()) {
            source.sendFailure(Component.literal("You must hold the Pin Wand in your main hand"));
            return false;
        }
        return true;
    }
}
