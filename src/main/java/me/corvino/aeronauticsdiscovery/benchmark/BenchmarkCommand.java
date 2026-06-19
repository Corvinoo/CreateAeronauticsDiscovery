package me.corvino.aeronauticsdiscovery.benchmark;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class BenchmarkCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("prefabbench")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                    .executes(ctx -> {
                        PrefabBenchmark.start();
                        ctx.getSource().sendSuccess(() -> Component.literal("§aBenchmark started."), true);
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("stop")
                    .executes(ctx -> {
                        PrefabBenchmark.stop();
                        for (String line : PrefabBenchmark.report().split("\n")) {
                            ctx.getSource().sendSuccess(() -> Component.literal(line), true);
                        }
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("report")
                    .executes(ctx -> {
                        for (String line : PrefabBenchmark.report().split("\n")) {
                            ctx.getSource().sendSuccess(() -> Component.literal(line), true);
                        }
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("reset")
                    .executes(ctx -> {
                        PrefabBenchmark.reset();
                        ctx.getSource().sendSuccess(() -> Component.literal("§eBenchmark stats reset."), true);
                        return Command.SINGLE_SUCCESS;
                    }))
                .executes(ctx -> {
                    for (String line : PrefabBenchmark.report().split("\n")) {
                        ctx.getSource().sendSuccess(() -> Component.literal(line), true);
                    }
                    return Command.SINGLE_SUCCESS;
                })
        );
    }
}
