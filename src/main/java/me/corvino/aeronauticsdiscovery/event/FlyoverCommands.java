package me.corvino.aeronauticsdiscovery.event;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;
import java.util.stream.Collectors;

public final class FlyoverCommands {
    private static final Random RANDOM = new Random();
    private static final int PENDING_TIMEOUT_TICKS = 600; // 30 seconds

    private static final Map<UUID, UUID> DEBUG_SESSIONS = new HashMap<>();

    private static final Map<UUID, ResourceLocation> PENDING_SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> PENDING_SPAWN_TICKS = new HashMap<>();

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
                        .then(Commands.literal("chunks")
                                .executes(ctx -> executeChunks(ctx.getSource()))
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

        var playerCDs = MacroChunkTracker.getPlayerCooldowns(level);
        var chunkCDs  = MacroChunkTracker.getChunkSpawnCooldowns(level);
        int eligibleCount = (int) playerCDs.values().stream().filter(cd -> cd == 0).count();

        StringBuilder sb = new StringBuilder();
        sb.append("Flyover events: ").append(FlyoverEventScheduler.isEnabled() ? "enabled" : "disabled");
        sb.append(" | ").append(configCount).append(" config(s)");
        sb.append(" | ").append(playerCDs.size()).append(" player(s)");
        sb.append(" | ").append(eligibleCount).append(" eligible");
        sb.append(" | ").append(activeCount).append(" active flyover(s)");
        sb.append(" | ").append(chunkCDs.size()).append(" chunk rate-limiter(s)");

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            int chunkSize = Config.macroChunkSize;
            long chunkKey = MacroChunkTracker.getChunkKey(player.blockPosition(), chunkSize);
            int cx = (int) (chunkKey >> 32);
            int cz = (int) chunkKey;
            BlockPos center = MacroChunkTracker.getChunkCenter(chunkKey, chunkSize);
            int personalCD = playerCDs.getOrDefault(player.getUUID(), -1);

            sb.append(" | You: chunk [").append(cx).append(", ").append(cz).append("]");
            sb.append(" center (").append(center.getX()).append(", ").append(center.getZ()).append(")");

            if (personalCD < 0) {
                sb.append(" | personal CD: ").append(ChatFormatting.YELLOW).append("new").append(ChatFormatting.RESET);
            } else if (personalCD == 0) {
                if (chunkCDs.containsKey(chunkKey)) {
                    sb.append(" | ").append(ChatFormatting.YELLOW).append("eligible (blocked by chunk)").append(ChatFormatting.RESET);
                } else {
                    sb.append(" | ").append(ChatFormatting.GREEN).append("eligible (ready!)").append(ChatFormatting.RESET);
                }
            } else {
                sb.append(" | personal CD: ").append(personalCD).append(" ticks");
            }

            sb.append(" | chunk CD: ");
            if (chunkCDs.containsKey(chunkKey)) {
                sb.append(ChatFormatting.RED).append(chunkCDs.get(chunkKey)).append(" ticks").append(ChatFormatting.RESET);
            } else {
                sb.append(ChatFormatting.GREEN).append("none").append(ChatFormatting.RESET);
            }
        }

        String msg = sb.toString();
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int executeChunks(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        var chunkCDs  = MacroChunkTracker.getChunkSpawnCooldowns(level);
        var playerCDs = MacroChunkTracker.getPlayerCooldowns(level);
        int chunkSize = Config.macroChunkSize;

        // Group online players by their macro chunk
        Map<Long, List<ServerPlayer>> playersByChunk = new HashMap<>();
        for (ServerPlayer p : level.players()) {
            playersByChunk.computeIfAbsent(
                    MacroChunkTracker.getChunkKey(p.blockPosition(), chunkSize),
                    k -> new ArrayList<>()
            ).add(p);
        }

        // Union of chunk keys from rate-limiters and player positions
        Set<Long> allKeys = new HashSet<>(chunkCDs.keySet());
        allKeys.addAll(playersByChunk.keySet());

        if (allKeys.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No macro chunks with rate-limiters or players."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("=== Macro Chunks (" + allKeys.size() + " total, size=" + chunkSize + ") ==="), false);

        List<Long> sortedKeys = new ArrayList<>(allKeys);
        Collections.sort(sortedKeys);

        for (long key : sortedKeys) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            BlockPos center = MacroChunkTracker.getChunkCenter(key, chunkSize);

            String header;
            if (chunkCDs.containsKey(key)) {
                int ticks = chunkCDs.get(key);
                header = String.format("  [%d, %d] center (%d, %d): %s%d ticks%s",
                        cx, cz, center.getX(), center.getZ(),
                        ChatFormatting.RED, ticks, ChatFormatting.RESET);
            } else {
                header = String.format("  [%d, %d] center (%d, %d): %sready%s",
                        cx, cz, center.getX(), center.getZ(),
                        ChatFormatting.GREEN, ChatFormatting.RESET);
            }
            source.sendSuccess(() -> Component.literal(header), false);

            List<ServerPlayer> chunkPlayers = playersByChunk.get(key);
            if (chunkPlayers != null) {
                for (ServerPlayer p : chunkPlayers) {
                    int cd = playerCDs.getOrDefault(p.getUUID(), -1);
                    String line;
                    if (cd < 0) {
                        line = String.format("    %s: %snew%s",
                                p.getName().getString(), ChatFormatting.YELLOW, ChatFormatting.RESET);
                    } else if (cd == 0) {
                        if (chunkCDs.containsKey(key)) {
                            line = String.format("    %s: %seligible (blocked by chunk)%s",
                                    p.getName().getString(), ChatFormatting.YELLOW, ChatFormatting.RESET);
                        } else {
                            line = String.format("    %s: %seligible (ready!)%s",
                                    p.getName().getString(), ChatFormatting.GREEN, ChatFormatting.RESET);
                        }
                    } else {
                        line = String.format("    %s: %d ticks", p.getName().getString(), cd);
                    }
                    source.sendSuccess(() -> Component.literal(line), false);
                }
            }
        }
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
                    : ChatFormatting.RED + "REMOVED" + ChatFormatting.RESET;
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
            FlyoverEventScheduler.spawnForPlayer(level, config, player, RANDOM);

            if (enableDebug) {
                PENDING_SESSIONS.put(player.getUUID(), config.template());
                PENDING_SPAWN_TICKS.put(player.getUUID(), level.getGameTime());
                source.sendSuccess(() -> Component.literal("Enqueued flyover '" + config.template().getPath()
                        + "'. Debug active - check your actionbar."), true);
            } else {
                source.sendSuccess(
                        () -> Component.literal("Enqueued flyover '" + config.template().getPath() + "' near " + player.getName().getString()),
                        true
                );
            }

            return 1;
        } catch (Exception e) {
            CreateAeronauticsDiscovery.LOGGER.warn("[FLYOVER] Command failed: {}", e.getMessage());
            source.sendFailure(Component.literal("Failed to enqueue flyover: " + e.getMessage()));
            return 0;
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID playerId = player.getUUID();

        // ---- Phase 1: resolved UUID-based tracking ----
        UUID flyoverId = DEBUG_SESSIONS.get(playerId);
        if (flyoverId != null) {
            ServerLevel level = player.serverLevel();
            FlyoverManager manager = FlyoverManager.get(level);
            FlyoverData data = manager.getFlyoverData(flyoverId);
            SubLevel subLevel = manager.getSubLevel(flyoverId);

            boolean alive = data != null && subLevel != null && !subLevel.isRemoved();
            boolean unloaded = data != null && subLevel == null;
            boolean expired = data != null && data.lifeTicks() >= Config.flyoverMaxLifetimeTicks;

            if (data == null && subLevel == null) {
                DEBUG_SESSIONS.remove(playerId);
                return;
            }

            String templateName = data != null ? data.templateId().getPath() : "unknown";
            String status;

            if (alive) {
                status = ChatFormatting.GREEN + "ALIVE" + ChatFormatting.RESET;
            } else if (unloaded) {
                status = (expired ? ChatFormatting.RED : ChatFormatting.YELLOW) + "UNLOADED" + ChatFormatting.RESET;
            } else {
                status = ChatFormatting.RED + "REMOVED" + ChatFormatting.RESET;
            }

            String lifeInfo;
            if (data != null) {
                int elapsedSec = data.lifeTicks() / 20;
                int maxSec = Config.flyoverMaxLifetimeTicks / 20;
                int pct = data.lifeTicks() * 100 / Config.flyoverMaxLifetimeTicks;
                lifeInfo = String.format("%d/%ds (%d%%) | %s", elapsedSec, maxSec, pct, status);
            } else {
                lifeInfo = "??/??s | " + status;
            }

            player.displayClientMessage(
                    Component.literal("\u2708 " + templateName + " | " + lifeInfo),
                    true
            );
            return;
        }

        // ---- Phase 2: pending templateId-based tracking (UUID not yet known) ----
        ResourceLocation templateId = PENDING_SESSIONS.get(playerId);
        if (templateId == null) return;

        ServerLevel level = player.serverLevel();
        long spawnTick = PENDING_SPAWN_TICKS.getOrDefault(playerId, 0L);
        long elapsed = level.getGameTime() - spawnTick;

        var matching = FlyoverManager.get(level).getAllFlyovers().entrySet().stream()
                .filter(e -> e.getValue().templateId().equals(templateId))
                .reduce((a, b) -> b);

        if (matching.isPresent()) {
            UUID resolvedId = matching.get().getKey();
            DEBUG_SESSIONS.put(playerId, resolvedId);
            PENDING_SESSIONS.remove(playerId);
            PENDING_SPAWN_TICKS.remove(playerId);
            return;
        }

        if (elapsed < PENDING_TIMEOUT_TICKS) {
            player.displayClientMessage(
                    Component.literal("\u2708 " + templateId.getPath() + " | " + ChatFormatting.YELLOW + "WAITING (" + elapsed + "t)"),
                    true
            );
        } else {
            PENDING_SESSIONS.remove(playerId);
            PENDING_SPAWN_TICKS.remove(playerId);
            player.displayClientMessage(
                    Component.literal("\u2708 " + templateId.getPath() + " | " + ChatFormatting.RED + "GONE"),
                    true
            );
        }
    }
}
