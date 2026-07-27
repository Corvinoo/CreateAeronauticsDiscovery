package me.corvino.aeronauticsdiscovery.loot;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery.MODID;

public final class ModLootFunctions {
    private ModLootFunctions() {}

    public static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTION_TYPES =
            DeferredRegister.create(BuiltInRegistries.LOOT_FUNCTION_TYPE, MODID);

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<?>> STRUCTURE_MAP =
            LOOT_FUNCTION_TYPES.register("structure_map",
                    () -> new LootItemFunctionType<>(StructureMapLootFunction.CODEC));
}
