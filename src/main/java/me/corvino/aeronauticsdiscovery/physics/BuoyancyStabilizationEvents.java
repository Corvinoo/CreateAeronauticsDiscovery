package me.corvino.aeronauticsdiscovery.physics;

import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.server.level.ServerLevel;

@EventBusSubscriber(modid = CreateAeronauticsDiscovery.MODID)
public final class BuoyancyStabilizationEvents {

    private BuoyancyStabilizationEvents() {}

    @SubscribeEvent
    public static void onPrePhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        ServerLevel level = event.getPhysicsSystem().getLevel();
        BuoyancyStabilizationManager.get(level).tickSubstep(event.getTimeStep());
    }
}