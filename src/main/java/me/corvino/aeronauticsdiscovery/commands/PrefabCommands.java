package me.corvino.aeronauticsdiscovery.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.corvino.aeronauticsdiscovery.assembly.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;

import static me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery.MODID;

public final class PrefabCommands {
    private PrefabCommands() {}

    private static final SuggestionProvider<CommandSourceStack> STRUCTURE_SUGGESTIONS =
            (ctx, builder) -> {
                var server = ctx.getSource().getServer();
                var paths = server.getResourceManager().listResources("structure", s -> s.getPath().endsWith(".nbt"));
                var suggestions = paths.keySet().stream()
                        .map(loc -> loc.getPath().replace("structure/", "").replace(".nbt", ""))
                        .toList();
                return SharedSuggestionProvider.suggest(suggestions, builder);
            };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("spawnprefab")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> executeSpawn(ctx.getSource(), null, null))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> executeSpawn(
                                        ctx.getSource(),
                                        null,
                                        BlockPosArgument.getBlockPos(ctx, "pos")
                                ))
                                .then(Commands.argument("structure", StringArgumentType.word())
                                        .suggests(STRUCTURE_SUGGESTIONS)
                                        .executes(ctx -> executeSpawn(
                                                ctx.getSource(),
                                                ResourceLocation.fromNamespaceAndPath(MODID, StringArgumentType.getString(ctx, "structure")),
                                                BlockPosArgument.getBlockPos(ctx, "pos")
                                        ))
                                )
                        )
        );
    }

    private static int executeSpawn(CommandSourceStack source, ResourceLocation structureId, BlockPos pos) {
        ServerLevel level = source.getLevel();

        if (structureId == null) {
            structureId = ResourceLocation.fromNamespaceAndPath(MODID, "balloon_loot");
        }
        if (pos == null) {
            pos = new BlockPos(0, 150, 0);
        }

        ResourceLocation finalId = structureId;
        BlockPos finalPos = pos;

        try {
            AssemblyContext ctx = AssemblyContext.builder(level, finalId, AssemblySource.COMMAND)
                    .anchor(finalPos)
                    .rotationTemplate(net.minecraft.world.level.block.Rotation.NONE)
                    .activationDistance(128)
                    .maxRetries(5)
                    .build();

            AssemblyQueue.get(level).enqueue(Pipelines.STANDARD, ctx);

            source.sendSuccess(
                    () -> Component.literal("Prefab '" + finalId.getPath() + "' enqueued for assembly at " + finalPos.toShortString()),
                    true
            );

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}
