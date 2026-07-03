package me.corvino.aeronauticsdiscovery.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public final class CleanChildSubLevelsCommand {
    private CleanChildSubLevelsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("cleanchildsl")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> executeRemove(ctx.getSource()))
                        .then(Commands.literal("list")
                                .executes(ctx -> executeList(ctx.getSource())))
        );
    }

    private static int executeRemove(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            source.sendFailure(Component.literal("SubLevelContainer unavailable"));
            return 0;
        }

        List<ServerSubLevel> children = findChildSubLevels(container);
        if (children.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No child sublevels found"), true);
            return 1;
        }

        int removed = 0;
        for (ServerSubLevel child : children) {
            FlyoverUtils.removeAllEntitiesInSublevel(child, false);
            container.removeSubLevel(child, SubLevelRemovalReason.REMOVED);
            removed++;
        }

        int finalRemoved = removed;
        source.sendSuccess(() -> Component.literal("Removed " + finalRemoved + " child sublevel(s)"), true);
        return removed;
    }

    private static int executeList(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            source.sendFailure(Component.literal("SubLevelContainer unavailable"));
            return 0;
        }

        List<ServerSubLevel> children = findChildSubLevels(container);
        if (children.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No child sublevels found"), true);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Found " + children.size() + " child sublevel(s):"), true);
        for (ServerSubLevel child : children) {
            CompoundTag tag = child.getUserDataTag();
            String parentId = tag != null && tag.hasUUID(FlyoverUtils.PARENT_SUBLEVEL_ID_TAG)
                    ? tag.getUUID(FlyoverUtils.PARENT_SUBLEVEL_ID_TAG).toString().substring(0, 8) + "..."
                    : "unknown";
            source.sendSuccess(() -> Component.literal("  - " + child.getUniqueId().toString().substring(0, 8) + "..."
                    + " (parent: " + parentId + ", name: " + child.getName() + ")"), true);
        }

        return children.size();
    }

    private static List<ServerSubLevel> findChildSubLevels(SubLevelContainer container) {
        return container.getAllSubLevels().stream()
                .filter(sl -> sl instanceof ServerSubLevel)
                .map(sl -> (ServerSubLevel) sl)
                .filter(sl -> {
                    CompoundTag tag = sl.getUserDataTag();
                    return tag != null && tag.hasUUID(FlyoverUtils.PARENT_SUBLEVEL_ID_TAG);
                })
                .toList();
    }
}
