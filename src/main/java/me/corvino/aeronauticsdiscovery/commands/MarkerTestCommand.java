package me.corvino.aeronauticsdiscovery.commands;

import com.mojang.brigadier.CommandDispatcher;
import me.corvino.aeronauticsdiscovery.items.MarkerWandItem;
import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import me.corvino.aeronauticsdiscovery.marker.MarkerTrigger;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehavior;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

public final class MarkerTestCommand {

    private MarkerTestCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("markertest")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> run(ctx.getSource()))
        );
    }

    private static int run(CommandSourceStack source) {
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }

        Level level = player.level();
        var hit = player.pick(10.0, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a block containing a marker"));
            return 0;
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        List<MarkerEntity> markers = level.getEntitiesOfClass(MarkerEntity.class, new AABB(pos).inflate(0.01), m -> m.blockPosition().equals(pos));
        if (markers.isEmpty()) {
            source.sendFailure(Component.literal("No marker found at the block you're looking at"));
            return 0;
        }

        MarkerEntity marker = markers.get(0);
        MarkerBehavior<?> behavior = marker.resolveBehavior();
        if (behavior == null) {
            source.sendFailure(Component.literal("Marker has no valid behavior configured"));
            return 0;
        }

        behavior.onAssembled(marker);

        MarkerTrigger trigger = new MarkerTrigger(MarkerTrigger.Kind.PLAYER_PROXIMITY, marker.position(), 0);
        behavior.onTrigger(marker, trigger);

        source.sendSuccess(() -> Component.literal("§8[§6✧§8] §aTriggered §f" + marker.getBehaviorId().getPath() + " §aat " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
        return 1;
    }
}
