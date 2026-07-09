package me.corvino.aeronauticsdiscovery.commands;

import com.mojang.brigadier.CommandDispatcher;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinNetwork;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
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

public final class PinTestCommand {

    private PinTestCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pintest")
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
            source.sendFailure(Component.literal("Look at a block containing a pin"));
            return 0;
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        List<PinEntity> pins = level.getEntitiesOfClass(PinEntity.class, new AABB(pos).inflate(0.01), m -> m.blockPosition().equals(pos));
        if (pins.isEmpty()) {
            source.sendFailure(Component.literal("No pin found at the block you're looking at"));
            return 0;
        }

        PinEntity pin = pins.get(0);

        PinTrigger trigger = new PinTrigger(PinTrigger.Kind.ASSEMBLED, pin.position());
        PinNetwork.triggerDirect(pin, trigger);

        source.sendSuccess(() -> Component.literal("§8[§6✧§8] §aTriggered §f" + pin.getBehaviorId().getPath() + " §aat " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
        return 1;
    }
}
