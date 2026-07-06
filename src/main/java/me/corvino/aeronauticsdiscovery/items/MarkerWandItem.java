package me.corvino.aeronauticsdiscovery.items;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.entities.EntityRegistry;
import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import me.corvino.aeronauticsdiscovery.marker.behaviour.ConfigField;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehaviorType;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehaviorTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager.FLYOVER_ID_TAG;

import java.util.List;

import static net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND;
import static net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND;

public class    MarkerWandItem extends Item {

    private static final String TAG_BEHAVIOR_ID = "BehaviorId";
    private static final String TAG_CONFIG = "Config";

    public MarkerWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) return InteractionResultHolder.success(player.getItemInHand(hand));
        ItemStack stack = player.getItemInHand(hand);
        initConfig(stack);
        player.sendSystemMessage(buildConfigUI(stack));
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;

        BlockPos pos = context.getClickedPos();

        MarkerEntity existing = findMarkerAt(level, pos);
        if (existing != null) {
            player.sendSystemMessage(buildMarkerInfoUI(existing));
            return InteractionResult.CONSUME;
        }

        initConfig(stack);
        CompoundTag tag = getDataTag(stack);
        String behaviorIdStr = tag.getString(TAG_BEHAVIOR_ID);
        if (behaviorIdStr.isEmpty()) return InteractionResult.FAIL;

        ResourceLocation behaviorId = ResourceLocation.tryParse(behaviorIdStr);
        if (behaviorId == null) return InteractionResult.FAIL;

        CompoundTag config = tag.getCompound(TAG_CONFIG);
        Vec3 epos = Vec3.atBottomCenterOf(pos);

        MarkerEntity marker = new MarkerEntity(EntityRegistry.MARKER.get(), level);
        marker.setPos(epos.x, epos.y, epos.z);
        marker.setBehavior(behaviorId, config);

        
        // Putting sublevel id into marker if created inside sublevel bounds  
        if (level instanceof ServerLevel serverLevel) {
            var container = SubLevelContainer.getContainer(serverLevel);
            if (container != null) {
                for (SubLevel sl : container.getAllSubLevels()) {
                    if (sl.boundingBox().toMojang().contains(epos)) {
                        marker.getPersistentData().putUUID(FLYOVER_ID_TAG, sl.getUniqueId());
                        break;
                    }
                }
            }
        }

        level.addFreshEntity(marker);

        player.sendSystemMessage(Component.literal(
                "§8[§6✧§8] §aPlaced §f" + behaviorId.getPath() + " §aat " +
                        Math.round(epos.x) + " " + Math.round(epos.y) + " " + Math.round(epos.z)));
        return InteractionResult.CONSUME;
    }

    public static MarkerEntity findMarkerAt(Level level, BlockPos pos) {
        for (MarkerEntity marker : level.getEntitiesOfClass(MarkerEntity.class, new AABB(pos).inflate(0.01))) {
            if (marker.blockPosition().equals(pos)) {
                return marker;
            }
        }
        return null;
    }

    public static Component buildMarkerInfoUI(MarkerEntity marker) {
        ResourceLocation id = marker.getBehaviorId();
        if (id == null) {
            return Component.literal("§c[Marker] No behavior configured on this entity");
        }

        MarkerBehaviorType<?> type = MarkerBehaviorTypes.byId(id);
        CompoundTag config = marker.getConfig();
        BlockPos pos = marker.blockPosition();

        MutableComponent msg = Component.literal("");
        msg.append(Component.literal("§8[§6- Marker Info - §f" + id.getPath() + "§8]\n"));
        msg.append(Component.literal("  §7Position: §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "\n"));

        if (type != null) {
            List<ConfigField> fields = type.configFields();
            if (!fields.isEmpty()) {
                msg.append(Component.literal("  §7Config:\n"));
                for (ConfigField field : fields) {
                    String valueStr = readConfigValue(config, field);
                    msg.append(Component.literal("    §7" + field.label() + ": §f" + valueStr + "\n"));
                }
            }
        }

        msg.append(Component.literal("  §7Bound: §" + (marker.isBound() ? "aYes" : "cNo") + "\n"));

        MutableComponent removeBtn = Component.literal("§8[§c✕ Remove§8]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(RUN_COMMAND,
                                "/markerwand remove " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("§cRemove this marker"))));
        msg.append(Component.literal("  "));
        msg.append(removeBtn);

        return msg;
    }

    public static Component buildConfigUI(ItemStack stack) {
        initConfig(stack);
        CompoundTag tag = getDataTag(stack);
        String behaviorIdStr = tag.getString(TAG_BEHAVIOR_ID);

        ResourceLocation behaviorId = ResourceLocation.tryParse(behaviorIdStr);
        if (behaviorId == null) {
            return Component.literal("§c[Marker Wand] No behavior configured - use /markerwand cycle");
        }

        MarkerBehaviorType<?> type = MarkerBehaviorTypes.byId(behaviorId);
        if (type == null) {
            return Component.literal("§c[Marker Wand] Unknown behavior: " + behaviorIdStr);
        }

        CompoundTag config = tag.getCompound(TAG_CONFIG);
        MutableComponent msg = Component.literal("");

        msg.append(Component.literal("§8[§6- Marker Wand - §f" + behaviorId.getPath() + "§8]\n"));

        MutableComponent cycleBtn = Component.literal("§8[§6\u2936 Cycle§8]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(RUN_COMMAND, "/markerwand cycle"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("§7Switch behavior type"))));
        msg.append(Component.literal("  §7Behavior: §f" + behaviorId.getPath() + "  "));
        msg.append(cycleBtn);
        msg.append(Component.literal("\n"));

        List<ConfigField> fields = type.configFields();
        if (!fields.isEmpty()) {
            msg.append(Component.literal("  §7Parameters:\n"));
            for (ConfigField field : fields) {
                String valueStr = readConfigValue(config, field);
                MutableComponent editBtn = Component.literal("§8[§6\u270e§8]")
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent(SUGGEST_COMMAND,
                                        "/markerwand set " + field.key() + " " + valueStr))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§7Edit " + field.label()))));
                msg.append(Component.literal("    §7" + field.label() + ": §f" + valueStr + "  "));
                msg.append(editBtn);
                msg.append(Component.literal("\n"));
            }
        }

        msg.append(Component.literal("  §7Right-click a block to place the marker"));
        return msg;
    }

    public static void initConfig(ItemStack stack) {
        CompoundTag tag = getDataTag(stack);
        if (tag.contains(TAG_BEHAVIOR_ID)) return;
        var all = MarkerBehaviorTypes.getAll();
        if (all.isEmpty()) return;
        var first = all.values().iterator().next();
        tag.putString(TAG_BEHAVIOR_ID, first.id().toString());
        tag.put(TAG_CONFIG, first.defaultConfig());
        setDataTag(stack, tag);
    }

    public static CompoundTag getDataTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    public static void setDataTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static String readConfigValue(CompoundTag config, ConfigField field) {
        return switch (field.type()) {
            case FLOAT -> String.valueOf(config.getFloat(field.key()));
            case DOUBLE -> String.valueOf(config.getDouble(field.key()));
            case INTEGER -> String.valueOf(config.getInt(field.key()));
            case STRING -> config.getString(field.key());
            case RESOURCE_LOCATION -> config.getString(field.key());
        };
    }

    public static void writeConfigValue(CompoundTag config, ConfigField field, String raw) {
        switch (field.type()) {
            case FLOAT -> config.putFloat(field.key(), Float.parseFloat(raw));
            case DOUBLE -> config.putDouble(field.key(), Double.parseDouble(raw));
            case INTEGER -> config.putInt(field.key(), Integer.parseInt(raw));
            case STRING -> config.putString(field.key(), raw);
            case RESOURCE_LOCATION -> config.putString(field.key(), ResourceLocation.parse(raw).toString());
        }
    }
}
