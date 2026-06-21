package me.corvino.aeronauticsdiscovery.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.corvino.aeronauticsdiscovery.assembly.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PipelineDebugCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("pipelinedebug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(ctx -> executeList(ctx.getSource())))
                        .then(Commands.literal("steps")
                                .executes(ctx -> executeSteps(ctx.getSource(), "flyover"))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) ->
                                                SharedSuggestionProvider.suggest(Pipelines.getAll().keySet(), builder))
                                        .executes(ctx -> executeSteps(
                                                ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("queue")
                                .executes(ctx -> executeQueue(ctx.getSource())))
                        .executes(ctx -> executeList(ctx.getSource()))
        );
    }

    private static int executeList(CommandSourceStack source) {
        var pipelines = Pipelines.getAll();
        source.sendSuccess(() -> Component.literal("§6=== Registered Pipelines (" + pipelines.size() + ") ===§r"), false);

        pipelines.values().stream()
                .sorted(Comparator.comparing(AssemblyPipeline::name))
                .forEach(p -> {
                    int count = p.steps().size();
                    source.sendSuccess(() -> Component.literal(
                            "  §e" + p.name() + "§r — " + count + " step(s)"), false);
                });

        source.sendSuccess(() -> Component.literal("§7Use /pipelinedebug steps <name> for details§r"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSteps(CommandSourceStack source, String name) {
        AssemblyPipeline pipeline;
        try {
            pipeline = Pipelines.byName(name);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown pipeline: " + name));
            return 0;
        }

        List<AssemblyStep> steps = pipeline.steps();
        source.sendSuccess(() -> Component.literal(
                "§6=== Pipeline: §e" + pipeline.name() + "§r §6(" + steps.size() + " steps) ===§r"), false);

        for (int i = 0; i < steps.size(); i++) {
            int index = i;
            AssemblyStep step = steps.get(i);
            source.sendSuccess(() -> Component.literal(
                    "  §b" + (index + 1) + ".§r " + step.getClass().getSimpleName()), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int executeQueue(CommandSourceStack source) {
        if (!(source.getLevel() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Must be used on server world."));
            return 0;
        }

        AssemblyQueue queue = AssemblyQueue.get(level);
        List<AssemblyQueue.Entry> entries = queue.getEntries();

        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§eAssembly queue is empty.§r"), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.literal(
                "§6=== Assembly Queue (" + entries.size() + " pending) ===§r"), false);

        for (int i = 0; i < entries.size(); i++) {
            AssemblyQueue.Entry entry = entries.get(i);
            AssemblyContext ctx = entry.context();
            int idx = i;

            source.sendSuccess(() -> Component.literal(
                    "  §b[" + idx + "]§r " + ctx.templateId.getPath()
                            + " §7|§r src=§e" + ctx.source.name().toLowerCase()
                            + "§r pipe=§e" + entry.pipeline().name()
                            + "§r trig=§e" + ctx.trigger.name().toLowerCase()
                            + "§r retry=§c" + entry.retryCount() + "/" + ctx.maxRetries
                            + "§r" + (ctx.assemblerPos != null ? " §7pos=" + ctx.assemblerPos.toShortString() : "")
            ), false);
        }

        return Command.SINGLE_SUCCESS;
    }
}
