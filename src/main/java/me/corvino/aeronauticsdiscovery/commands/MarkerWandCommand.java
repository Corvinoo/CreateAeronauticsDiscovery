package me.corvino.aeronauticsdiscovery.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.corvino.aeronauticsdiscovery.items.ItemRegistry;
import me.corvino.aeronauticsdiscovery.items.MarkerWandItem;
import me.corvino.aeronauticsdiscovery.marker.behaviour.ConfigField;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehaviorType;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehaviorTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MarkerWandCommand {
    private static final String TAG_BEHAVIOR_ID = "BehaviorId";
    private static final String TAG_CONFIG = "Config";

    private MarkerWandCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("markerwand")
                .requires(source -> source.hasPermission(0))
                .then(Commands.literal("cycle")
                        .executes(ctx -> cycle(ctx.getSource())))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> set(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key"),
                                                StringArgumentType.getString(ctx, "value"))))))
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
