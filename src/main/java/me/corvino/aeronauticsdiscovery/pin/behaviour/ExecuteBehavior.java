package me.corvino.aeronauticsdiscovery.pin.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.PIN;

public record ExecuteBehavior(String command, ResourceLocation function)
        implements PinBehavior<ExecuteBehavior> {

    public static final ResourceLocation EMPTY_FUNCTION_ID = ResourceLocation.parse("minecraft:empty");

    public static final PinBehaviorType<ExecuteBehavior> TYPE = PinBehaviorTypes.<ExecuteBehavior>register(
            "execute",
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("command", "").forGetter(ExecuteBehavior::command),
                    Codec.STRING.optionalFieldOf("function", EMPTY_FUNCTION_ID.toString())
                            .xmap(ExecuteBehavior::parseFunctionId, ResourceLocation::toString)
                            .forGetter(ExecuteBehavior::function)
            ).apply(instance, ExecuteBehavior::new)),
            List.of(
                    new ConfigField("command", "Command", ConfigField.FieldType.STRING, ""),
                    new ConfigField("function", "Function", ConfigField.FieldType.RESOURCE_LOCATION, EMPTY_FUNCTION_ID)
            ),
            0x80FFD040
    );

    @Override
    public PinBehaviorType<ExecuteBehavior> type() {
        return TYPE;
    }

    @Override
    public void onTrigger(PinEntity self, PinTrigger trigger) {
        if (!Config.executeEnabled) {
            Component message = Component.literal("§l[§b§lCreate Aeronautics Discovery§r§l]§r: Execute Pin tried to run at " + self.position() + " but execution is disabled from the configs");
            PlayerList playerList = self.level().getServer().getPlayerList();
            for (ServerPlayer player : Objects.requireNonNull(playerList.getPlayers())) {
                if (playerList.isOp(player.getGameProfile())) {
                    player.sendSystemMessage(message);
                }
            }  
            return;
        }
        if (!(self.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();

        Vec3 triggerPos = trigger.originWorldPos();
        CommandSourceStack source = new CommandSourceStack(
                CommandSource.NULL, self.position(), Vec2.ZERO, level, 2,
                "CommandPin", Component.literal("CommandPin"), server, null);

        runInlineCommand(server, source, self, trigger, triggerPos);
        runFunction(server, source, self, trigger, triggerPos);
    }
    

    private void runInlineCommand(MinecraftServer server, CommandSourceStack source,
            PinEntity self, PinTrigger trigger, Vec3 triggerPos) {
        String expanded = replacePlaceholder(this.command, self.position(), triggerPos).strip();
        if (expanded.isEmpty()) return;
        String cmd = expanded.startsWith("/") ? expanded.substring(1).strip() : expanded;
        try {
            server.getCommands().performPrefixedCommand(source, cmd);
        } catch (Exception e) {
            ModLog.warn(PIN,
                    "Execute pin at {} failed command '{}' (trigger {} @ {})",
                    self.blockPosition(), cmd, trigger.kind(), triggerPos, e.toString());
        }
    }

    //Runs the configured datapack function
    private void runFunction(MinecraftServer server, CommandSourceStack source,
            PinEntity self, PinTrigger trigger, Vec3 triggerPos) {
        if (!hasFunction()) return;
        var resolved = server.getFunctions().get(this.function);
        if (resolved.isEmpty()) {
            ModLog.warn(PIN,
                    "Execute pin at {} references missing function '{}' (trigger {} @ {})",
                    self.blockPosition(), this.function, trigger.kind(), triggerPos);
            return;
        }
        try {
            server.getFunctions().execute(resolved.get(), source);
        } catch (Exception e) {
            ModLog.warn(PIN,
                    "Execute pin at {} failed function '{}' (trigger {} @ {})",
                    self.blockPosition(), this.function, trigger.kind(), triggerPos, e.toString());
        }
    }

    private boolean hasFunction() {
        return this.function != null && !EMPTY_FUNCTION_ID.equals(this.function);
    }

    private static ResourceLocation parseFunctionId(String raw) {
        if (raw == null || raw.isBlank()) return EMPTY_FUNCTION_ID;
        ResourceLocation parsed = ResourceLocation.tryParse(raw.strip());
        return parsed != null ? parsed : EMPTY_FUNCTION_ID;
    }

    static String replacePlaceholder(String template, Vec3 pinPos, Vec3 triggerPos) {
        if (template == null) return "";
        String out = template
                .replace("{x}", Double.toString(pinPos.x()))
                .replace("{y}", Double.toString(pinPos.y()))
                .replace("{z}", Double.toString(pinPos.z()));
        if (triggerPos != null) {
            out = out
                    .replace("{trigger_x}", Double.toString(triggerPos.x()))
                    .replace("{trigger_y}", Double.toString(triggerPos.y()))
                    .replace("{trigger_z}", Double.toString(triggerPos.z()));
        }
        return out;
    }
}
