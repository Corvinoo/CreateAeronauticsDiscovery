package me.corvino.aeronauticsdiscovery.event;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class FlyoverCommands {
    private static final Random RANDOM = new Random();

    private static final Map<UUID, UUID> DEBUG_SESSIONS = new HashMap<>();

    private FlyoverCommands() {}

    private static final SuggestionProvider<CommandSourceStack> FLYOVER_SUGGESTIONS =
            (ctx, builder) -> {
                var registry = FlyoverEventRegistry.getInstance();
                if (registry == null) return builder.buildFuture();
                var suggestions = registry.getConfigs().stream()
                        .map(c -> c.template().getPath())
                        .toList();
                return SharedSuggestionProvider.suggest(suggestions, builder);
            };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var debugBranch = Commands.literal("debug")
                .executes(ctx -> spawnWithDebug(ctx.getSource(), null, null))
                .then(Commands.argument("structure", StringArgumentType.word())
                        .suggests(FLYOVER_SUGGESTIONS)
                        .executes(ctx -> spawnWithDebug(ctx.getSource(), null, StringArgumentType.getString(ctx, "structure")))
                )
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> spawnWithDebug(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), null))
                        .then(Commands.argument("structure", StringArgumentType.word())
                                .suggests(FLYOVER_SUGGESTIONS)
                                .executes(ctx -> spawnWithDebug(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        StringArgumentType.getString(ctx, "structure")
                                ))
                        )
                );

        dispatcher.register(
                Commands.literal("flyover")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("toggle")
                                .executes(ctx -> executeToggle(ctx.getSource()))
                        )
                        .then(Commands.literal("status")
                                .executes(ctx -> executeStatus(ctx.getSource()))
                        )
                        .then(Commands.literal("list")
                                .executes(ctx -> executeList(ctx.getSource()))
                        )
                        .then(debugBranch)
                        .executes(ctx -> spawn(ctx.getSource(), null, null, false))
                        .then(Commands.argument("structure", StringArgumentType.word())
                                .suggests(FLYOVER_SUGGESTIONS)
                                .executes(ctx -> spawn(ctx.getSource(), null, StringArgumentType.getString(ctx, "structure"), false))
                        )
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> spawn(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), null, false))
                                .then(Commands.argument("structure", StringArgumentType.word())
                                        .suggests(FLYOVER_SUGGESTIONS)
                                        .executes(ctx -> spawn(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "structure"),
                                                false
                                        ))
                                )
                        )
        );
    }

    private static int executeToggle(CommandSourceStack source) {
        boolean now = FlyoverEventScheduler.toggleEnabled();
        source.sendSuccess(
                () -> Component.literal("Flyover events " + (now ? "enabled" : "disabled")),
                true
        );
        return 1;
    }

    private static int executeStatus(CommandSourceStack source) {
        var registry = FlyoverEventRegistry.getInstance();
        int configCount = registry != null ? registry.getConfigs().size() : 0;

        ServerLevel level = source.getLevel();
        FlyoverManager manager = FlyoverManager.get(level);
        int activeCount = manager.getAllFlyovers().size();

        source.sendSuccess(
                () -> Component.literal("Flyover events: " + (FlyoverEventScheduler.isEnabled() ? "enabled" : "disabled")
                        + " | " + configCount + " config(s) loaded"
                        + " | " + activeCount + " active flyover(s)"),
                false
        );
        return 1;
    }

    private static int executeList(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        FlyoverManager manager = FlyoverManager.get(level);
        var all = manager.getAllFlyovers();

        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active flyovers."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("=== Active Flyovers (" + all.size() + ") ==="), false);
        for (Map.Entry<UUID, FlyoverData> entry : all.entrySet()) {
            UUID id = entry.getKey();
            FlyoverData data = entry.getValue();
            SubLevel subLevel = manager.getSubLevel(id);
            boolean alive = subLevel != null && !subLevel.isRemoved();
            int remaining = Config.flyoverMaxLifetimeTicks - data.lifeTicks();
            String status = alive
                    ? ChatFormatting.GREEN + "ALIVE" + ChatFormatting.RESET + " (" + remaining + " ticks left)"
                    : ChatFormatting.RED + "DESPAWNED" + ChatFormatting.RESET;
            source.sendSuccess(() -> Component.literal(
                    "  " + data.templateId().getPath() + " | " + id + " | " + status
            ), false);
        }
        return 1;
    }

    private static int spawnWithDebug(CommandSourceStack source, ServerPlayer target, String structureName) {
        return spawn(source, target, structureName, true);
    }

    private static int spawn(CommandSourceStack source, ServerPlayer target, String structureName, boolean enableDebug) {
        ServerLevel level = source.getLevel();
        ServerPlayer player = target != null ? target : source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Must specify a target player when running from console."));
            return 0;
        }

        FlyoverEventRegistry registry = FlyoverEventRegistry.getInstance();
        if (registry == null || registry.getConfigs().isEmpty()) {
            source.sendFailure(Component.literal("No flyover event configs loaded."));
            return 0;
        }

        FlyoverEventConfig config;
        if (structureName != null) {
            ResourceLocation templateId = ResourceLocation.fromNamespaceAndPath(
                    CreateAeronauticsDiscovery.MODID, structureName
            );
            config = registry.getConfigs().stream()
                    .filter(c -> c.template().equals(templateId))
                    .findFirst()
                    .orElse(null);
            if (config == null) {
                source.sendFailure(Component.literal("Unknown flyover structure: " + structureName));
                return 0;
            }
        } else {
            config = registry.pickRandom(RANDOM);
            if (config == null) {
                source.sendFailure(Component.literal("No flyover event configs available."));
                return 0;
            }
        }

        try {
            SimAssemblyHelper.AssemblyResult result = FlyoverEventScheduler.spawnForPlayer(level, config, player, RANDOM);

            UUID flyoverId = result.subLevel().getUniqueId();

            if (enableDebug) {
                DEBUG_SESSIONS.put(player.getUUID(), flyoverId);
                source.sendSuccess(() -> Component.literal("Spawned flyover '" + config.template().getPath()
                        + "' (UUID: " + flyoverId + "). Debug active — check your actionbar."), true);
            } else {
                source.sendSuccess(
                        () -> Component.literal("Spawned flyover '" + config.template().getPath() + "' near " + player.getName().getString()),
                        true
                );
            }

            return 1;
        } catch (Exception e) {
            CreateAeronauticsDiscovery.LOGGER.warn("[FLYOVER] Command failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Failed to spawn flyover: " + e.getMessage()));
            return 0;
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID flyoverId = DEBUG_SESSIONS.get(player.getUUID());
        if (flyoverId == null) return;

        ServerLevel level = player.serverLevel();
        FlyoverManager manager = FlyoverManager.get(level);

        FlyoverData data = manager.getFlyoverData(flyoverId);
        SubLevel subLevel = manager.getSubLevel(flyoverId);

        boolean managerTracked = data != null;
        boolean subLevelExists = subLevel != null;
        boolean worldAlive = subLevelExists && !subLevel.isRemoved();

        // Auto-clean when flyover is completely gone from both tracking and world
        if (!managerTracked && !subLevelExists) {
            DEBUG_SESSIONS.remove(player.getUUID());
            return;
        }

        String templateName = data != null ? data.templateId().getPath() : "unknown";
        Component msg;

        if (managerTracked && worldAlive) {
            int elapsedSec = data.lifeTicks() / 20;
            int maxSec = Config.flyoverMaxLifetimeTicks / 20;
            msg = Component.literal(String.format(
                    "\u2708 %s | %d/%ds (%d%%) | %sTRACKED | %sALIVE",
                    templateName,
                    elapsedSec, maxSec, (data.lifeTicks() * 100 / Config.flyoverMaxLifetimeTicks),
                    ChatFormatting.GREEN, ChatFormatting.GREEN
            ));
        } else if (managerTracked && !subLevelExists) {
            int elapsedSec = data.lifeTicks() / 20;
            int maxSec = Config.flyoverMaxLifetimeTicks / 20;
            boolean expired = data.lifeTicks() >= Config.flyoverMaxLifetimeTicks;
            String worldState = expired ? "PENDING_REMOVAL" : "UNLOADED";
            ChatFormatting color = expired ? ChatFormatting.RED : ChatFormatting.YELLOW;
            msg = Component.literal(String.format(
                    "\u2708 %s | %d/%ds (%d%%) | %sTRACKED | %s%s",
                    templateName,
                    elapsedSec, maxSec, (data.lifeTicks() * 100 / Config.flyoverMaxLifetimeTicks),
                    ChatFormatting.GREEN, color, worldState
            ));
        } else {
            String managerStatus = managerTracked
                    ? ChatFormatting.GREEN + "TRACKED"
                    : ChatFormatting.RED + "REMOVED";
            String worldStatus = worldAlive
                    ? ChatFormatting.GREEN + "ALIVE"
                    : subLevelExists
                            ? ChatFormatting.RED + "REMOVED"
                            : ChatFormatting.RED + "GONE";
            msg = Component.literal(String.format(
                    "\u2708 %s | %sDESPAWNED | Manager: %s%s | World: %s%s",
                    templateName,
                    ChatFormatting.RED, managerStatus, ChatFormatting.RESET,
                    worldStatus, ChatFormatting.RESET
            ));
        }

        player.displayClientMessage(msg, true);
    }
}
