package me.corvino.aeronauticsdiscovery.items;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.entities.EntityRegistry;
import me.corvino.aeronauticsdiscovery.pin.EmitterConfig;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
import me.corvino.aeronauticsdiscovery.pin.TriggerMask;
import me.corvino.aeronauticsdiscovery.pin.behaviour.ConfigField;
import me.corvino.aeronauticsdiscovery.pin.behaviour.PinBehaviorType;
import me.corvino.aeronauticsdiscovery.pin.behaviour.PinBehaviorTypes;
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

import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;

import java.util.List;

import static net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND;
import static net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND;

public class    PinWandItem extends Item {

    private static final String TAG_BEHAVIOR_ID = "BehaviorId";
    private static final String TAG_CONFIG = "Config";
    private static final String TAG_TRIGGER_MASK = "TriggerMask";
    private static final String TAG_EMITTER_RADIUS = "EmitterRadius";
    private static final String TAG_EMITTER_SPEED = "EmitterSpeed";

    public PinWandItem(Properties properties) {
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

        PinEntity existing = findPinAt(level, pos);
        if (existing != null) {
            player.sendSystemMessage(buildPinInfoUI(existing));
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

        PinEntity pin = new PinEntity(EntityRegistry.PIN.get(), level);
        pin.setPos(epos.x, epos.y, epos.z);
        pin.setBehavior(behaviorId, config);
        pin.setTriggerMask(getTriggerMask(tag));
        pin.setEmitterConfig(getEmitterConfig(tag));

        
        // Tag pin with sublevel UUID if placed inside a sublevel's bounding box.
        // Uses Sable.HELPER.getContaining() (sl.boundingBox() returns plot-local, not world coordinates).
        if (level instanceof ServerLevel serverLevel) {
            SubLevel sl = Sable.HELPER.getContaining(serverLevel, epos);
            if (sl != null) {
                pin.getPersistentData().putUUID(SUBLEVEL_ID_TAG, sl.getUniqueId());
            }
        }

        level.addFreshEntity(pin);

        player.sendSystemMessage(Component.literal(
                "§8[§6✧§8] §aPlaced §f" + behaviorId.getPath() + " §aat " +
                        Math.round(epos.x) + " " + Math.round(epos.y) + " " + Math.round(epos.z)));
        return InteractionResult.CONSUME;
    }

    public static PinEntity findPinAt(Level level, BlockPos pos) {
        for (PinEntity pin : level.getEntitiesOfClass(PinEntity.class, new AABB(pos).inflate(0.01))) {
            if (pin.blockPosition().equals(pos)) {
                return pin;
            }
        }
        return null;
    }

    public static Component buildPinInfoUI(PinEntity pin) {
        ResourceLocation id = pin.getBehaviorId();
        if (id == null) {
            return Component.literal("§c[Pin] No behavior configured on this entity");
        }

        PinBehaviorType<?> type = PinBehaviorTypes.byId(id);
        CompoundTag config = pin.getConfig();
        BlockPos pos = pin.blockPosition();

        MutableComponent msg = Component.literal("");
        msg.append(Component.literal("§8[§6- Pin Info - §f" + id.getPath() + "§8]\n"));
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

        msg.append(Component.literal("  §7Triggers: " + triggerMaskSummary(pin.getTriggerMask()) + "\n"));
        msg.append(Component.literal("  §7Emitter: " + emitterSummary(pin.getEmitterConfig()) + "\n"));
        msg.append(Component.literal("  §7Bound: §" + (pin.isBound() ? "aYes" : "cNo") + "\n"));

        MutableComponent removeBtn = Component.literal("§8[§c✕ Remove§8]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(RUN_COMMAND,
                                "/pinwand remove " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("§cRemove this pin"))));
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
            return Component.literal("§c[Pin Wand] No behavior configured - use /pinwand cycle");
        }

        PinBehaviorType<?> type = PinBehaviorTypes.byId(behaviorId);
        if (type == null) {
            return Component.literal("§c[Pin Wand] Unknown behavior: " + behaviorIdStr);
        }

        CompoundTag config = tag.getCompound(TAG_CONFIG);
        MutableComponent msg = Component.literal("");

        msg.append(Component.literal("§8[§6- Pin Wand - §f" + behaviorId.getPath() + "§8]\n"));

        MutableComponent cycleBtn = Component.literal("§8[§6\u2936 Cycle§8]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(RUN_COMMAND, "/pinwand cycle"))
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
                                        "/pinwand set " + field.key() + " " + valueStr))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§7Edit " + field.label()))));
                msg.append(Component.literal("    §7" + field.label() + ": §f" + valueStr + "  "));
                msg.append(editBtn);
                msg.append(Component.literal("\n"));
            }
        }

        TriggerMask mask = getTriggerMask(tag);
        msg.append(Component.literal("  §7Triggers: " + triggerMaskSummary(mask) + "  "));
        MutableComponent editTriggersBtn = Component.literal("§8[§6✎ Triggers§8]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(RUN_COMMAND, "/pinwand trigger"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("§7Configure which trigger kinds activate this pin"))));
        msg.append(editTriggersBtn);
        msg.append(Component.literal("\n"));

        EmitterConfig emitter = getEmitterConfig(tag);
        msg.append(Component.literal("  §7Emitter: " + emitterSummary(emitter) + "  "));
        MutableComponent editEmitterBtn = Component.literal("§8[§6✎ Emitter§8]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(RUN_COMMAND, "/pinwand emitter"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("§7Configure trigger propagation"))));
        msg.append(editEmitterBtn);
        msg.append(Component.literal("\n"));

        msg.append(Component.literal("  §7Right-click a block to place the pin"));
        return msg;
    }

    public static void initConfig(ItemStack stack) {
        CompoundTag tag = getDataTag(stack);
        if (!tag.contains(TAG_TRIGGER_MASK, CompoundTag.TAG_INT)) {
            setTriggerMask(tag, TriggerMask.NONE);
        }
        if (tag.contains(TAG_BEHAVIOR_ID)) {
            setDataTag(stack, tag);
            return;
        }
        var all = PinBehaviorTypes.getAll();
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

    public static TriggerMask getTriggerMask(CompoundTag tag) {
        if (!tag.contains(TAG_TRIGGER_MASK, CompoundTag.TAG_INT)) return TriggerMask.NONE;
        return new TriggerMask(tag.getInt(TAG_TRIGGER_MASK));
    }

    public static void setTriggerMask(CompoundTag tag, TriggerMask mask) {
        tag.putInt(TAG_TRIGGER_MASK, mask.bits());
    }

    public static void toggleTriggerKind(ItemStack stack, PinTrigger.Kind kind) {
        CompoundTag tag = getDataTag(stack);
        TriggerMask mask = getTriggerMask(tag);
        if (mask.accepts(kind)) {
            mask = mask.without(kind);
        } else {
            mask = mask.with(kind);
        }
        setTriggerMask(tag, mask);
        setDataTag(stack, tag);
    }

    public static Component buildTriggerMaskUI(ItemStack stack) {
        CompoundTag tag = getDataTag(stack);
        TriggerMask mask = getTriggerMask(tag);

        MutableComponent msg = Component.literal("");
        msg.append(Component.literal("§8[§6- Edit Triggers -§8]\n"));

        for (PinTrigger.Kind kind : PinTrigger.Kind.values()) {
            boolean enabled = mask.accepts(kind);
            String icon = enabled ? "§a✓" : "§8✕";
            String color = enabled ? "§a" : "§8";
            MutableComponent toggleBtn = Component.literal("§8[§7" + icon + "§8]")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(RUN_COMMAND,
                                    "/pinwand trigger " + kind.name()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal(enabled
                                            ? "§cClick to disable " + kind.displayName()
                                            : "§aClick to enable " + kind.displayName()))));
            msg.append(Component.literal("  "));
            msg.append(toggleBtn);
            msg.append(Component.literal(" " + color + kind.displayName() + "\n"));
        }

        MutableComponent backBtn = Component.literal("§8[§6← Back§8]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(RUN_COMMAND, "/pinwand"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("§7Return to main wand config"))));
        msg.append(Component.literal("  "));
        msg.append(backBtn);

        return msg;
    }

    public static EmitterConfig getEmitterConfig(CompoundTag tag) {
        if (!tag.contains(TAG_EMITTER_RADIUS, CompoundTag.TAG_DOUBLE)) return EmitterConfig.DISABLED;
        double radius = tag.getDouble(TAG_EMITTER_RADIUS);
        double speed = tag.contains(TAG_EMITTER_SPEED, CompoundTag.TAG_DOUBLE)
                ? tag.getDouble(TAG_EMITTER_SPEED) : 5.0;
        return new EmitterConfig(radius, speed);
    }

    public static void setEmitterConfig(CompoundTag tag, EmitterConfig config) {
        if (!config.isEnabled()) {
            tag.remove(TAG_EMITTER_RADIUS);
            tag.remove(TAG_EMITTER_SPEED);
            return;
        }
        tag.putDouble(TAG_EMITTER_RADIUS, config.radius());
        tag.putDouble(TAG_EMITTER_SPEED, config.propagationSpeed());
    }

    public static Component buildEmitterUI(ItemStack stack) {
        CompoundTag tag = getDataTag(stack);
        EmitterConfig emitter = getEmitterConfig(tag);

        MutableComponent msg = Component.literal("");
        msg.append(Component.literal("§8[§6- Edit Emitter -§8]\n"));

        String radiusStr = emitter.isEnabled() ? "§a" + emitter.radius() : "§8(off)";
        msg.append(Component.literal("  §7Radius: " + radiusStr + "  "));
        MutableComponent editRadiusBtn = Component.literal("§8[§6\u270e§8]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(SUGGEST_COMMAND,
                                "/pinwand emitter radius " + (emitter.isEnabled() ? emitter.radius() : "10.0")))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("§7Set propagation radius (0 = disabled)"))));
        msg.append(editRadiusBtn);
        msg.append(Component.literal("\n"));

        String speedStr = emitter.isEnabled() ? "§a" + emitter.propagationSpeed() + " §7b/t" : "§8-";
        msg.append(Component.literal("  §7Speed: " + speedStr + "  "));
        MutableComponent editSpeedBtn = Component.literal("§8[§6\u270e§8]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(SUGGEST_COMMAND,
                                "/pinwand emitter speed " + (emitter.isEnabled() ? emitter.propagationSpeed() : "5.0")))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("§7Set propagation speed in blocks/tick"))));
        msg.append(editSpeedBtn);
        msg.append(Component.literal("\n"));

        MutableComponent backBtn = Component.literal("§8[§6← Back§8]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(RUN_COMMAND, "/pinwand"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("§7Return to main wand config"))));
        msg.append(Component.literal("  "));
        msg.append(backBtn);

        return msg;
    }

    public static void setEmitterRadius(ItemStack stack, double radius) {
        CompoundTag tag = getDataTag(stack);
        double speed = tag.contains(TAG_EMITTER_SPEED, CompoundTag.TAG_DOUBLE)
                ? tag.getDouble(TAG_EMITTER_SPEED) : 5.0;
        if (radius <= 0) {
            tag.remove(TAG_EMITTER_RADIUS);
            tag.remove(TAG_EMITTER_SPEED);
        } else {
            tag.putDouble(TAG_EMITTER_RADIUS, radius);
            tag.putDouble(TAG_EMITTER_SPEED, speed);
        }
        setDataTag(stack, tag);
    }

    public static void setEmitterSpeed(ItemStack stack, double speed) {
        CompoundTag tag = getDataTag(stack);
        double radius = tag.contains(TAG_EMITTER_RADIUS, CompoundTag.TAG_DOUBLE)
                ? tag.getDouble(TAG_EMITTER_RADIUS) : 0;
        if (radius <= 0) return;
        tag.putDouble(TAG_EMITTER_SPEED, speed);
        setDataTag(stack, tag);
    }

    private static String emitterSummary(EmitterConfig emitter) {
        if (!emitter.isEnabled()) return "§8(off)";
        return "§a" + emitter.radius() + " §7@ §a" + emitter.propagationSpeed() + " §7b/t";
    }

    private static String triggerMaskSummary(TriggerMask mask) {
        if (mask.isEmpty()) return "§8(none)";
        StringBuilder sb = new StringBuilder();
        for (PinTrigger.Kind kind : PinTrigger.Kind.values()) {
            if (mask.accepts(kind)) {
                if (!sb.isEmpty()) sb.append("§7, ");
                sb.append("§a").append(kind.displayName());
            }
        }
        return sb.toString();
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
