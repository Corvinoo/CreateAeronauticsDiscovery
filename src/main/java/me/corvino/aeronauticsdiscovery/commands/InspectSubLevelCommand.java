package me.corvino.aeronauticsdiscovery.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.corvino.aeronauticsdiscovery.child.ChildRole;
import me.corvino.aeronauticsdiscovery.child.ChildSubLevelManager;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class InspectSubLevelCommand {

    private InspectSubLevelCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("discovery")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("inspectsl")
                                .executes(ctx -> execute(ctx.getSource()))
                        )
        );
    }

    private static int execute(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        HitResult hit = player.pick(200.0, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            source.sendFailure(Component.literal("Not looking at a block"));
            return 0;
        }

        BlockPos pos = blockHit.getBlockPos();
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel == null) {
            source.sendFailure(Component.literal("No sublevel at " + pos.toShortString()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== SubLevel at " + pos.toShortString() + " ==="), false);
        source.sendSuccess(() -> Component.literal("UUID: " + subLevel.getUniqueId()), false);
        source.sendSuccess(() -> Component.literal("Name: " + subLevel.getName()), false);
        source.sendSuccess(() -> Component.literal("Removed: " + subLevel.isRemoved()), false);

        if (subLevel instanceof ServerSubLevel ssl) {
            source.sendSuccess(() -> Component.literal("Level: " + ssl.getLevel().dimension().location()), false);

            CompoundTag userData = ssl.getUserDataTag();
            if (userData != null) {
                source.sendSuccess(() -> Component.literal("UserData: " + userData), false);

                if (userData.hasUUID(ChildSubLevelManager.PARENT_SUBLEVEL_ID_TAG)) {
                    var parentId = userData.getUUID(ChildSubLevelManager.PARENT_SUBLEVEL_ID_TAG);
                    String roleKey = userData.contains(ChildSubLevelManager.CHILD_ROLE_TAG)
                            ? userData.getString(ChildSubLevelManager.CHILD_ROLE_TAG) : "none";
                    ChildRole role = ChildRole.fromKey(roleKey);

                    SubLevelContainer container = SubLevelContainer.getContainer(level);
                    boolean parentExists = container != null && container.getSubLevel(parentId) != null;

                    source.sendSuccess(() -> Component.literal("Parent UUID: " + parentId), false);
                    source.sendSuccess(() -> Component.literal("Role: " + role + " (" + roleKey + ")"), false);
                    source.sendSuccess(() -> Component.literal("Parent exists: " + parentExists), false);
                    source.sendSuccess(() -> Component.literal("ORPHANED: " + (!parentExists)), false);
                }
            } else {
                source.sendSuccess(() -> Component.literal("UserData: null"), false);
            }
        }

        return 1;
    }
}
