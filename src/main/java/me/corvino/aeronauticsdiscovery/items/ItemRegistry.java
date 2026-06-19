package me.corvino.aeronauticsdiscovery.items;

import me.corvino.aeronauticsdiscovery.entities.EntityRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery.MODID;

public class ItemRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredHolder<Item, SpawnEggItem> SOARING_TRADER_SPAWN_EGG =
            ITEMS.register("soaring_trader_spawn_egg",
                    () -> new SpawnEggItem(EntityRegistry.SOARING_TRADER.get(), 0x300200, 0xffd800, new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
