package me.corvino.aeronauticsdiscovery.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.content.contraptions.AssemblyException;
import me.corvino.aeronauticsdiscovery.TestAirshipSpawner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;

public final class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("spawnairship")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();

                            try {
                                TestAirshipSpawner.spawnTinyShip(level);
                            } catch (AssemblyException e) {
                                throw new RuntimeException(e);
                            }

                            return 1;
                        })
        );
    }
}