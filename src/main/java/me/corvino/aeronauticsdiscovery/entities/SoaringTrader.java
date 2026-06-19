package me.corvino.aeronauticsdiscovery.entities;

import javax.annotation.Nullable;
import me.corvino.aeronauticsdiscovery.util.StructureSearchWorker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class SoaringTrader extends WanderingTrader {
    private static final String[] CREATE_FOODS = {
        "create:bar_of_chocolate",
        "create:sweet_roll",
        "create:chocolate_glazed_berries",
        "create:honeyed_apple",
        "create:builders_tea"
    };
    private static final String[] CARDBOARD_ITEMS = {
        "create:cardboard_sword",
        "create:cardboard_helmet",
        "create:cardboard_chestplate",
        "create:cardboard_leggings",
        "create:cardboard_boots"
    };
    private static final StructureEntry[] STRUCTURES = {
        new StructureEntry(BuiltinStructures.WOODLAND_MANSION, MapDecorationTypes.WOODLAND_MANSION, "filled_map.mansion"),
        new StructureEntry(BuiltinStructures.JUNGLE_TEMPLE, MapDecorationTypes.JUNGLE_TEMPLE, "filled_map.aeronauticsdiscovery.jungle_temple"),
        new StructureEntry(BuiltinStructures.DESERT_PYRAMID, MapDecorationTypes.TARGET_X, "filled_map.aeronauticsdiscovery.desert_pyramid"),
        new StructureEntry(BuiltinStructures.TRAIL_RUINS, MapDecorationTypes.TARGET_X, "filled_map.aeronauticsdiscovery.trail_ruins"),
        new StructureEntry(BuiltinStructures.IGLOO, MapDecorationTypes.TARGET_X, "filled_map.aeronauticsdiscovery.igloo"),
        new StructureEntry(BuiltinStructures.ANCIENT_CITY, MapDecorationTypes.TARGET_X, "filled_map.aeronauticsdiscovery.ancient_city"),
        new StructureEntry(BuiltinStructures.MINESHAFT, MapDecorationTypes.TARGET_X, "filled_map.aeronauticsdiscovery.mineshaft"),
        new StructureEntry(BuiltinStructures.TRIAL_CHAMBERS, MapDecorationTypes.TRIAL_CHAMBERS, "filled_map.trial_chambers"),
    };

    private record StructureEntry(ResourceKey<Structure> key, Holder<MapDecorationType> decoration, String nameKey) {}

    private static final String[] DYE_COLORS = {
        "white", "orange", "magenta", "light_blue",
        "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue",
        "brown", "green", "red", "black"
    };

    public SoaringTrader(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);
    }

    @Override
    protected void updateTrades() {
        MerchantOffers offers = this.getOffers();
        offers.clear();

        ItemStack map = buildMapTrade();
        if (map != null) {
            offers.add(new MerchantOffer(
                    new ItemCost(Items.EMERALD, 5),
                    map,
                    1, 0, 0.0F
            ));
        }

        offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 3),
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:brass_ingot")), 1),
                16, 0, 0.0F
        ));
        offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 2),
                new ItemStack(Items.DRIED_KELP, 2),
                32, 0, 0.0F
        ));
        offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 1),
                PotionContents.createItemStack(
                        Items.POTION,
                        BuiltInRegistries.POTION.wrapAsHolder(Potions.LEAPING.value())
                ),
                4, 0, 0.0F
        ));
        offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 2),
                new ItemStack(randomFood(), 1),
                12, 0, 0.0F
        ));
        offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 2),
                new ItemStack(randomCardboardItem(), 1),
                4, 0, 0.0F
        ));
        offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 5),
                new ItemStack(randomClockOrToolbox(), 1),
                2, 0, 0.0F
        ));
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Nullable
    private ItemStack buildMapTrade() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return null;

        StructureEntry entry = STRUCTURES[this.random.nextInt(STRUCTURES.length)];
        Registry<Structure> registry = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Holder<Structure> holder = registry.getHolderOrThrow(entry.key());

        ChunkGeneratorStructureState state = serverLevel.getChunkSource().getGeneratorState();
        long seed = state.getLevelSeed();

        for (StructurePlacement sp : state.getPlacementsForStructure(holder)) {
            if (sp instanceof RandomSpreadStructurePlacement rssp) {
                BlockPos found = StructureSearchWorker.searchNearest(
                        serverLevel, holder.value(), rssp, seed, this.blockPosition(), 50, 800);
                if (found != null) {
                    ItemStack map = MapItem.create(serverLevel, found.getX(), found.getZ(), (byte) 2, true, true);
                    MapItemSavedData.addTargetDecoration(map, found, "+", entry.decoration());
                    map.set(DataComponents.ITEM_NAME, Component.translatable(entry.nameKey()));
                    return map;
                }
            }
        }
        return null;
    }

    private Item randomFood() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(CREATE_FOODS[this.random.nextInt(CREATE_FOODS.length)]));
    }

    private Item randomCardboardItem() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(CARDBOARD_ITEMS[this.random.nextInt(CARDBOARD_ITEMS.length)]));
    }

    private Item randomClockOrToolbox() {
        if (this.random.nextBoolean()) {
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:cuckoo_clock"));
        } else {
            String color = DYE_COLORS[this.random.nextInt(DYE_COLORS.length)];
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:" + color + "_toolbox"));
        }
    }
}
