package me.corvino.aeronauticsdiscovery.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import javax.annotation.Nullable;
import me.corvino.aeronauticsdiscovery.util.StructureSearchWorker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public final class DebugCommands {
    private DebugCommands() {}

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Structure>[] DEBUG_TARGETS = new ResourceKey[] {
        BuiltinStructures.VILLAGE_PLAINS,
        BuiltinStructures.VILLAGE_DESERT,
        BuiltinStructures.VILLAGE_SAVANNA,
        BuiltinStructures.VILLAGE_SNOWY,
        BuiltinStructures.VILLAGE_TAIGA,
        BuiltinStructures.WOODLAND_MANSION,
        BuiltinStructures.JUNGLE_TEMPLE,
        BuiltinStructures.DESERT_PYRAMID,
        BuiltinStructures.TRAIL_RUINS,
        BuiltinStructures.IGLOO,
        BuiltinStructures.ANCIENT_CITY,
        BuiltinStructures.MINESHAFT,
        BuiltinStructures.TRIAL_CHAMBERS,
    };

    private static final String[] DEBUG_NAMES = {
        "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga",
        "woodland_mansion", "jungle_temple", "desert_pyramid", "trail_ruins", "igloo",
        "ancient_city", "mineshaft", "trial_chambers",
    };

    private static final ResourceKey<Structure> KEY_BY_NAME = null; // sentinel, resolved via lookup below

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("debugfindstructure")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> executeFind(ctx, null))
                        .then(Commands.argument("structure", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(DEBUG_NAMES, builder))
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "structure");
                                    ResourceKey<Structure> key = resolveKey(name);
                                    if (key == null) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown structure: " + name));
                                        return 0;
                                    }
                                    return executeFind(ctx, key);
                                }))
        );
    }

    private static ResourceKey<Structure> resolveKey(String name) {
        return switch (name) {
            case "village_plains" -> BuiltinStructures.VILLAGE_PLAINS;
            case "village_desert" -> BuiltinStructures.VILLAGE_DESERT;
            case "village_savanna" -> BuiltinStructures.VILLAGE_SAVANNA;
            case "village_snowy" -> BuiltinStructures.VILLAGE_SNOWY;
            case "village_taiga" -> BuiltinStructures.VILLAGE_TAIGA;
            case "woodland_mansion" -> BuiltinStructures.WOODLAND_MANSION;
            case "jungle_temple" -> BuiltinStructures.JUNGLE_TEMPLE;
            case "desert_pyramid" -> BuiltinStructures.DESERT_PYRAMID;
            case "trail_ruins" -> BuiltinStructures.TRAIL_RUINS;
            case "igloo" -> BuiltinStructures.IGLOO;
            case "ancient_city" -> BuiltinStructures.ANCIENT_CITY;
            case "mineshaft" -> BuiltinStructures.MINESHAFT;
            case "trial_chambers" -> BuiltinStructures.TRIAL_CHAMBERS;
            default -> null;
        };
    }

    private static int executeFind(CommandContext<CommandSourceStack> ctx, ResourceKey<Structure> keyArg) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());

        ResourceKey<Structure> key = keyArg;
        if (key == null) {
            key = DEBUG_TARGETS[level.random.nextInt(DEBUG_TARGETS.length)];
        }
        final ResourceKey<Structure> finalKey = key;

        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Holder<Structure> holder = registry.getHolderOrThrow(finalKey);
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        long levelSeed = state.getLevelSeed();
        List<StructurePlacement> placements = state.getPlacementsForStructure(holder);

        RandomSpreadStructurePlacement spread = findSpreadPlacement(placements);
        if (spread == null) {
            source.sendFailure(Component.literal("No RandomSpreadStructurePlacement for " + finalKey.location()));
            return 0;
        }
        final RandomSpreadStructurePlacement finalSpread = spread;

        source.sendSuccess(() -> Component.literal(
                "Searching for " + finalKey.location() + " from " + origin.toShortString()
                + " (spacing=" + finalSpread.spacing() + ", seed=" + levelSeed + ")"), true);

        long startTime = System.nanoTime();

        new StructureSearchWorker(
                level, holder.value(), finalSpread, levelSeed, origin,
                (foundPos, placement) -> {
                    long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                    int distance = (int) Math.sqrt(origin.distSqr(foundPos));
                    source.sendSuccess(() -> Component.literal(
                            "Found " + finalKey.location() + " at " + foundPos.toShortString()
                            + " (" + distance + " blocks away, " + elapsed + "ms)"), true);
                },
                () -> {
                    long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                    source.sendFailure(Component.literal(
                            "Search exhausted for " + finalKey.location() + " (" + elapsed + "ms)"));
                }
        ).start();

        return 1;
    }

    @Nullable
    private static RandomSpreadStructurePlacement findSpreadPlacement(List<StructurePlacement> placements) {
        for (StructurePlacement p : placements) {
            if (p instanceof RandomSpreadStructurePlacement rssp) {
                return rssp;
            }
        }
        return null;
    }
}
