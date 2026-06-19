package me.corvino.aeronauticsdiscovery.entities;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery.MODID;

public class EntityRegistry {

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SoaringTrader>> SOARING_TRADER = ENTITIES.register("soaring_trader",
            () -> EntityType.Builder.of(SoaringTrader::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("soaring_trader"));

    public static void RegisterEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(SOARING_TRADER.get(), Mob.createMobAttributes()
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .build());
    }
    
}
