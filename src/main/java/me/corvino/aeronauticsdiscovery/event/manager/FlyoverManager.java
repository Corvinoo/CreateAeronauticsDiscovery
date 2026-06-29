package me.corvino.aeronauticsdiscovery.event.manager;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.event.FlyoverSubLevelObserver;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery.LOGGER;

public class FlyoverManager extends SavedData {

    public static final String FLYOVER_ID_TAG = "flyover_sublevel_id";
    public static final TicketController ticketController = new TicketController(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsDiscovery.MODID, "chunkticketmanager")
    );

    private static final String DATA_NAME = CreateAeronauticsDiscovery.MODID + "_flyovers";
    private static final String TAG_KEY = "Flyovers";

    final Map<UUID, FlyoverData> flyovers = new LinkedHashMap<>();
    private final Queue<UUID> externalRemovalQueue = new LinkedList<>();

    private ServerLevel level;
    private boolean observerRegistered = false;

    public FlyoverManager() {
        this(null);
    }

    public FlyoverManager(ServerLevel level) {
        this.level = level;
    }

    public static FlyoverManager get(ServerLevel level) {
        FlyoverManager manager = level.getDataStorage().computeIfAbsent(
                new Factory<>(FlyoverManager::new, (tag, provider) -> load(level, tag), null),
                DATA_NAME);
        manager.level = level;
        manager.ensureObserverRegistered();
        return manager;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            get(serverLevel).tick();
        }
    }

    private void ensureObserverRegistered() {
        if (observerRegistered) return;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            container.addObserver(new FlyoverSubLevelObserver(this));
            observerRegistered = true;
        }
    }


    // Adds flyover to tracking
    public void addFlyover(SubLevel subLevel, ResourceLocation templateId) {
        FlyoverData entry = FlyoverData.fresh(subLevel.getUniqueId(), templateId);
        flyovers.put(entry.subLevelId(), entry);
        setDirty();
        LOGGER.info("[FLYOVER] Registered '{}' (id={}) - despawns after {} ticks or on player approach",
                templateId, subLevel.getUniqueId(), Config.flyoverMaxLifetimeTicks);
    }

    /**
     * Enqueues a tracked flyover for removal on the next tick.
     * Does not touch the SubLevel itself
     */
    public void enqueueExternalRemoval(UUID id) {
        externalRemovalQueue.add(id);
    }

    @Nullable
    public FlyoverData getEntry(UUID subLevelId) {
        return flyovers.get(subLevelId);
    }

    @Nullable
    public ServerSubLevel getSubLevel(UUID subLevelId) {
        if (level == null) return null;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        SubLevel found = container.getSubLevel(subLevelId);
        return found instanceof ServerSubLevel ssl ? ssl : null;
    }

    public Map<UUID, FlyoverData> getAllFlyovers() {
        return Collections.unmodifiableMap(flyovers);
    }

    public void tick() {
        if (flyovers.isEmpty()) return;

        final int maxLifetime = Config.flyoverMaxLifetimeTicks;
        final List<UUID> pendingRemoval = new ArrayList<>();

        for (FlyoverData entry : List.copyOf(flyovers.values())) {
            tickEntry(entry, maxLifetime, pendingRemoval);
        }

        drainExternalRemovals(pendingRemoval);

        if (!pendingRemoval.isEmpty()) {
            pendingRemoval.forEach(flyovers::remove);
            setDirty();
        }
    }

    // Increments tick for flyover or removes them
    private void tickEntry(FlyoverData entry, int maxLifetime, List<UUID> pendingRemoval) {
        ServerSubLevel subLevel = getSubLevel(entry.subLevelId());

        if (subLevel == null) {
            tickWhileUnloaded(entry, maxLifetime);
            return;
        }

        if (subLevel.isRemoved()) {
            // Already cleaned up externally; just drop our tracking record
            pendingRemoval.add(entry.subLevelId());
            return;
        }

        if (isPlayerNearSubLevel(subLevel)) {
            release(entry, subLevel);
            pendingRemoval.add(entry.subLevelId());
            return;
        }

        if (entry.isPastGracePeriod() && isTooFarFromAllPlayers(subLevel)) {
            despawn(entry, subLevel, FlyoverRemovalReason.DRIFTED_TOO_FAR);
            pendingRemoval.add(entry.subLevelId());
            return;
        }

        entry.incrementTick();

        if (entry.isExpired(maxLifetime)) {
            despawn(entry, subLevel, FlyoverRemovalReason.LIFETIME_EXPIRED);
            pendingRemoval.add(entry.subLevelId());
        }
    }

    // Advances lifetime for a flyover whose SubLevel is not currently loaded
    private void tickWhileUnloaded(FlyoverData entry, int maxLifetime) {
        if (entry.isExpired(maxLifetime)) return; // already capped
        entry.incrementTick();
        if (entry.isExpired(maxLifetime)) {
            LOGGER.debug("[FLYOVER] {} ('{}') expired while unloaded - will despawn on next chunk load",
                    entry.subLevelId(), entry.templateId());
        }
    }

    private void drainExternalRemovals(List<UUID> pendingRemoval) {
        UUID id;
        while ((id = externalRemovalQueue.poll()) != null) {
            if (!pendingRemoval.contains(id) && flyovers.containsKey(id)) {
                LOGGER.info("[FLYOVER] Removing {} via external request", id);
                pendingRemoval.add(id);
            }
        }
    }


    private void release(FlyoverData entry, ServerSubLevel subLevel) {
        LOGGER.info("[FLYOVER] Releasing {} ('{}') - player approached - handing off to Sable",
                entry.subLevelId(), entry.templateId());
        removeForceTicket(subLevel);
    }

    private void despawn(FlyoverData entry, ServerSubLevel subLevel, FlyoverRemovalReason reason) {
        LOGGER.info("[FLYOVER] Despawning {} ('{}') - {}",
                entry.subLevelId(), entry.templateId(), reason.describe());
        //todo: the entity cleanup cam ne finicky here, ideally it should be inside the observer and be called *after* the removal of a sublevel
        //it's currently placed here to avoid weird concurrency crash caused by the aquifier noise cache (yes. the water caverns.)
        FlyoverUtils.removeAllEntitiesInSublevel(subLevel, false); 
        removeSubLevelFromWorld(subLevel);
    }

    private void removeForceTicket(ServerSubLevel subLevel) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            LOGGER.warn("[FLYOVER] Cannot remove force ticket for {} - SubLevelContainer unavailable",
                    subLevel.getUniqueId());
            return;
        }
        container.removeForceLoadTicket(subLevel, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    }

    private void removeSubLevelFromWorld(ServerSubLevel subLevel) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            LOGGER.warn("[FLYOVER] Cannot remove SubLevel {} - SubLevelContainer unavailable",
                    subLevel.getUniqueId());
            return;
        }
        container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
    }

    private boolean isPlayerNearSubLevel(SubLevel subLevel) {
        AABB proximityBox = subLevel.boundingBox().toMojang().inflate(5.0);
        for (ServerPlayer player : level.players()) {
            if (proximityBox.contains(player.position().x, player.position().y, player.position().z)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTooFarFromAllPlayers(SubLevel subLevel) {
        AABB bb = subLevel.boundingBox().toMojang();
        // Bounding box not yet initialized on the first tick after assembly; deferring to next tick
        if (bb.minX == 0 && bb.minY == 0 && bb.minZ == 0
                && bb.maxX == 0 && bb.maxY == 0 && bb.maxZ == 0) {
            return false;
        }
        double cx = (bb.minX + bb.maxX) / 2.0;
        double cz = (bb.minZ + bb.maxZ) / 2.0;
        int viewDist = level.getServer().getPlayerList().getViewDistance();
        double despawnRadius   = viewDist * 16.0 + Config.flyoverMaxUnloadDistance;
        double despawnRadiusSq = despawnRadius * despawnRadius;
        for (ServerPlayer player : level.players()) {
            double dx = cx - player.getX();
            double dz = cz - player.getZ();
            if (dx * dx + dz * dz < despawnRadiusSq) return false;
        }
        return true;
    }
    

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        List<FlyoverData> snapshot = List.copyOf(flyovers.values());
        tag.put(TAG_KEY, FlyoverData.CODEC.listOf().encodeStart(NbtOps.INSTANCE, snapshot).getOrThrow());
        return tag;
    }

    private static FlyoverManager load(ServerLevel level, CompoundTag tag) {
        FlyoverManager manager = new FlyoverManager(level);
        if (tag.contains(TAG_KEY, 9 /* ListTag */)) {
            ListTag list = tag.getList(TAG_KEY, 10 /* CompoundTag */);
            FlyoverData.CODEC.listOf()
                    .parse(NbtOps.INSTANCE, list)
                    .resultOrPartial(err -> LOGGER.warn("[FLYOVER] Failed to parse flyover data: {}", err))
                    .ifPresent(entries -> entries.forEach(e -> manager.flyovers.put(e.subLevelId(), e)));
        }
        return manager;
    }
}