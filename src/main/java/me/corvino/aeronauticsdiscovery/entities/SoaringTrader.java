package me.corvino.aeronauticsdiscovery.entities;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import me.corvino.aeronauticsdiscovery.util.StructureSearchWorker;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.TRADE;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.Math.atan2;

public class SoaringTrader extends WanderingTrader {
    private int angryTicks;

    public void makeAngry(int ticks) {
        if (angryTicks <= 0 && ticks > 0) applyAnger();
        else if (angryTicks > 0 && ticks <= 0) resetAnger();
        angryTicks = ticks;
    }

    private void applyAnger() {
        MerchantOffers offers = getOffers();
        for (MerchantOffer offer : offers) {
            offer.setSpecialPriceDiff(offer.getItemCostA().count());
        }
        syncOffers();
    }

    private void resetAnger() {
        MerchantOffers offers = getOffers();
        for (MerchantOffer offer : offers) {
            offer.resetSpecialPriceDiff();
        }
        syncOffers();
    }

    private void syncOffers() {
        if (this.getTradingPlayer() instanceof ServerPlayer player && player.connection != null)
            player.connection.send(new ClientboundMerchantOffersPacket(
                    player.containerMenu.containerId, this.getOffers(), 0, 0, false, false));
    }

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
    private record StructureMeta(Holder<MapDecorationType> decoration, String nameKey) {}

    private static final Map<String, StructureMeta> KNOWN_STRUCTURES = Map.ofEntries(
            Map.entry("minecraft:woodland_mansion", new StructureMeta(MapDecorationTypes.WOODLAND_MANSION, "filled_map.mansion")),
            Map.entry("minecraft:jungle_pyramid", new StructureMeta(MapDecorationTypes.JUNGLE_TEMPLE, "filled_map.aeronauticsdiscovery.jungle_temple")),
            Map.entry("minecraft:trial_chambers", new StructureMeta(MapDecorationTypes.TRIAL_CHAMBERS, "filled_map.trial_chambers"))
    );

    private static final String[] DYE_COLORS = {
            "white", "orange", "magenta", "light_blue",
            "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black"
    };

    public SoaringTrader(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);
    }

    private static final double PITCH_THRESHOLD_ON = Math.toRadians(9);
    private static final double PITCH_THRESHOLD_OFF = Math.toRadians(7);
    private static final double ROLL_THRESHOLD_ON = Math.toRadians(1);
    private static final double ROLL_THRESHOLD_OFF = Math.toRadians(0);
    private final StabilizerTransmitter pitchUpSignal = new StabilizerTransmitter(this, Items.GREEN_WOOL);
    private final StabilizerTransmitter pitchDownSignal = new StabilizerTransmitter(this, Items.YELLOW_WOOL);
    private final StabilizerTransmitter rollRightSignal = new StabilizerTransmitter(this, Items.RED_WOOL);
    private final StabilizerTransmitter rollLeftSignal = new StabilizerTransmitter(this, Items.LIGHT_BLUE_WOOL);

    private double minAltitude = 160.0;
    private static final double ALTITUDE_BIAS_PER_BLOCK = Math.toRadians(0.4);
    private static final double MAX_ALTITUDE_BIAS = Math.toRadians(12);
    private boolean linksRegistered = false;

    private StabilizerTransmitter[] allTransmitters() {
        return new StabilizerTransmitter[]{pitchUpSignal, pitchDownSignal, rollRightSignal, rollLeftSignal};
    }

    private void registerLinksIfNeeded(ServerLevel serverLevel) {
        if (linksRegistered) return;
        for (StabilizerTransmitter t : allTransmitters())
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(serverLevel, t);
        linksRegistered = true;
    }

    private void unregisterLinks(ServerLevel serverLevel) {
        if (!linksRegistered) return;
        for (StabilizerTransmitter t : allTransmitters())
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(serverLevel, t);
        linksRegistered = false;
    }

    private void setActive(ServerLevel serverLevel, StabilizerTransmitter t, boolean shouldBeActive) {
        if (t.isActive() == shouldBeActive) return;
        t.setActive(shouldBeActive);
        Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(serverLevel, t);
    }

    private void setAllInactive(ServerLevel serverLevel) {
        for (StabilizerTransmitter t : allTransmitters())
            setActive(serverLevel, t, false);
    }

    private static boolean hysteresis(boolean currentlyActive, double value, double onThreshold, double offThreshold) {
        return currentlyActive ? value > offThreshold : value > onThreshold;
    }

    public double getMinAltitude() {
        return minAltitude;
    }

    public void setMinAltitude(double minAltitude) {
        this.minAltitude = minAltitude;
    }

    private double getWorldAltitude() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return Double.MAX_VALUE;
        Vector3d worldPos = Sable.HELPER.projectOutOfSubLevel(
                serverLevel, JOMLConversion.atCenterOf(this.blockPosition()));
        return worldPos.y();
    }

    private void tickStabilizer(ServerLevel serverLevel) {
        registerLinksIfNeeded(serverLevel);

        var seatEntityMaybe = this.getRootVehicle();

        SubLevel subLevel = Sable.HELPER.getContaining(seatEntityMaybe);
        if (subLevel == null) {
            setAllInactive(serverLevel);
            return;
        }

        Pose3dc pose = subLevel.logicalPose();
        Vector3d localDown = JOMLConversion.toJOML(new Vec3(0, -1, 0));
        pose.orientation().transformInverse(localDown);

        double pitch = (localDown.y() < 0 || localDown.z() * localDown.z() > 0.001)
                ? atan2(localDown.z(), -localDown.y()) : 0;
        double roll = (localDown.y() < 0 || localDown.x() * localDown.x() > 0.001)
                ? atan2(localDown.x(), -localDown.y()) : 0;


        double altitudeDeficit = minAltitude - getWorldAltitude();
        double altitudeBias = altitudeDeficit > 0
                ? Mth.clamp(altitudeDeficit * ALTITUDE_BIAS_PER_BLOCK, 0, MAX_ALTITUDE_BIAS)
                : 0;
        double adjustedPitch = pitch - altitudeBias;

        boolean pitchTooLow = hysteresis(pitchUpSignal.isActive(), -adjustedPitch, PITCH_THRESHOLD_ON, PITCH_THRESHOLD_OFF);
        boolean pitchTooHigh = hysteresis(pitchDownSignal.isActive(), adjustedPitch, PITCH_THRESHOLD_ON, PITCH_THRESHOLD_OFF);
        boolean rollTooLeft = hysteresis(rollRightSignal.isActive(), -roll, ROLL_THRESHOLD_ON, ROLL_THRESHOLD_OFF);
        boolean rollTooRight = hysteresis(rollLeftSignal.isActive(), roll, ROLL_THRESHOLD_ON, ROLL_THRESHOLD_OFF);

        setActive(serverLevel, pitchUpSignal, pitchTooLow);
        setActive(serverLevel, pitchDownSignal, pitchTooHigh);
        setActive(serverLevel, rollRightSignal, rollTooLeft);
        setActive(serverLevel, rollLeftSignal, rollTooRight);
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

        if (angryTicks > 0) applyAnger();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.removeAllGoals(goal -> goal instanceof UseItemGoal);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && source.getEntity() instanceof ServerPlayer)
            makeAngry(Config.traderAngerDuration);
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel) {
            tickStabilizer(serverLevel);
            tickAnger(serverLevel);
        }
    }

    private void tickAnger(ServerLevel serverLevel) {
        if (angryTicks <= 0) return;
        angryTicks--;
        if (angryTicks <= 0) {
            resetAnger();
            return;
        }
        if (angryTicks % 20 == 0)
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    this.getRandomX(0.5), this.getY(1.0), this.getRandomZ(0.5),
                    1, 0.0, 0.0, 0.0, 0.0);
    }

    @Nullable
    private ItemStack buildMapTrade() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return null;

        List<? extends String> ids = Config.traderStructureMaps;
        ModLog.debug(TRADE, "ids={}", ids);
        if (ids.isEmpty()) return null;

        Registry<Structure> registry = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ChunkGeneratorStructureState state = serverLevel.getChunkSource().getGeneratorState();
        long seed = state.getLevelSeed();

        if (Config.traderGuaranteedMap) {
            List<String> shuffled = new ArrayList<>(ids);
            for (int i = shuffled.size() - 1; i > 0; i--) {
                int j = this.random.nextInt(i + 1);
                String tmp = shuffled.get(i);
                shuffled.set(i, shuffled.get(j));
                shuffled.set(j, tmp);
            }
            for (String id : shuffled) {
                ItemStack map = tryStructure(serverLevel, registry, state, seed, id);
                if (map != null) return map;
            }
            return null;
        }

        return tryStructure(serverLevel, registry, state, seed,
                ids.get(this.random.nextInt(ids.size())));
    }

    @Nullable
    private ItemStack tryStructure(ServerLevel serverLevel, Registry<Structure> registry,
                                   ChunkGeneratorStructureState state, long seed, String structId) {
        ResourceLocation loc = ResourceLocation.parse(structId);
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, loc);
        ModLog.debug(TRADE, "tryStructure: {}", structId);
        var holderOpt = registry.getHolder(key);
        if (holderOpt.isEmpty()) {
            ModLog.warn(TRADE, "unknown structure: {}", structId);
            return null;
        }
        for (StructurePlacement sp : state.getPlacementsForStructure(holderOpt.get())) {
            if (sp instanceof RandomSpreadStructurePlacement rssp) {
                BlockPos found = StructureSearchWorker.searchNearest(
                        serverLevel, holderOpt.get().value(), rssp, seed, this.blockPosition(), 50, 800);
                ModLog.debug(TRADE, "searchNearest({}) = {}", structId, found);
                if (found != null) {
                    StructureMeta meta = KNOWN_STRUCTURES.get(structId);
                    Holder<MapDecorationType> deco = meta != null ? meta.decoration() : MapDecorationTypes.TARGET_X;
                    ItemStack map = MapItem.create(serverLevel, found.getX(), found.getZ(), (byte) 2, true, true);
                    MapItemSavedData.addTargetDecoration(map, found, "+", deco);
                    if (meta != null) {
                        map.set(DataComponents.ITEM_NAME, Component.translatable(meta.nameKey()));
                    } else {
                        String[] parts = loc.getPath().split("_");
                        StringBuilder name = new StringBuilder();
                        for (String p : parts) {
                            name.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
                        }
                        name.append("Map");
                        map.set(DataComponents.ITEM_NAME, Component.literal(name.toString()));
                    }
                    return map;
                }
            }
        }
        ModLog.debug(TRADE, "no placement found for {}", structId);
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


    private static final class StabilizerTransmitter implements IRedstoneLinkable {
        private final SoaringTrader owner;
        private final Couple<RedstoneLinkNetworkHandler.Frequency> key;
        private boolean active = false;

        StabilizerTransmitter(SoaringTrader owner, Item woolItem) {
            this.owner = owner;
            RedstoneLinkNetworkHandler.Frequency freq = RedstoneLinkNetworkHandler.Frequency.of(new ItemStack(woolItem));
            this.key = Couple.create(freq, freq);
        }

        boolean isActive() {
            return active;
        }

        void setActive(boolean active) {
            this.active = active;
        }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public int getTransmittedStrength() {
            return active ? 15 : 0;
        }

        @Override
        public void setReceivedStrength(int networkPower) {

        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return key;
        }

        @Override
        public boolean isAlive() {
            return owner.isAlive() && !owner.isRemoved() && owner.level() != null;
        }

        @Override
        public BlockPos getLocation() {
            return owner.blockPosition();
        }
    }
}
