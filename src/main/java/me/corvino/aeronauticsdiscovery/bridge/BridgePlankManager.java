package me.corvino.aeronauticsdiscovery.bridge;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.joml.Vector3d;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.BRIDGE;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.registries.BuiltInRegistries;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class BridgePlankManager extends SavedData {

    private static final String DATA_NAME = CreateAeronauticsDiscovery.MODID + "_bridge_planks";

    private final Map<UUID, List<PlankInfo>> planksByRope = new Object2ObjectOpenHashMap<>();

    public record PlankInfo(int plankIndex, UUID subLevelUUID, BlockState slabState) {}

    public static SavedData.Factory<BridgePlankManager> factory() {
        return new SavedData.Factory<>(BridgePlankManager::new, BridgePlankManager::load);
    }

    public static BridgePlankManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static BridgePlankManager load(CompoundTag tag, HolderLookup.Provider registries) {
        BridgePlankManager manager = new BridgePlankManager();
        if (tag.contains("planks_by_rope")) {
            CompoundTag ropes = tag.getCompound("planks_by_rope");
            for (String key : ropes.getAllKeys()) {
                UUID ropeUUID = UUID.fromString(key);
                ListTag list = ropes.getList(key, Tag.TAG_COMPOUND);
                List<PlankInfo> infos = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag entry = list.getCompound(i);
                    int plankIdx = entry.getInt("plank_index");
                    UUID slUUID = entry.getUUID("sub_level_uuid");
                    BlockState slabState = Blocks.OAK_SLAB.defaultBlockState();
                    if (entry.contains("slab_block")) {
                        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(entry.getString("slab_block")));
                        if (block != null) slabState = block.defaultBlockState();
                    }
                    infos.add(new PlankInfo(plankIdx, slUUID, slabState));
                }
                manager.planksByRope.put(ropeUUID, infos);
            }
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag ropes = new CompoundTag();
        for (var entry : planksByRope.entrySet()) {
            ListTag list = new ListTag();
            for (PlankInfo info : entry.getValue()) {
                CompoundTag plankEntry = new CompoundTag();
                plankEntry.putInt("plank_index", info.plankIndex());
                plankEntry.putUUID("sub_level_uuid", info.subLevelUUID());
                plankEntry.putString("slab_block", BuiltInRegistries.BLOCK.getKey(info.slabState().getBlock()).toString());
                list.add(plankEntry);
            }
            ropes.put(entry.getKey().toString(), list);
        }
        tag.put("planks_by_rope", ropes);
        return tag;
    }

    public synchronized int addPlank(UUID ropeUUID, UUID subLevelUUID, int plankIndex, BlockState slabState) {
        var list = planksByRope.computeIfAbsent(ropeUUID, k -> new ArrayList<>());
        list.add(new PlankInfo(plankIndex, subLevelUUID, slabState));
        setDirty();
        return plankIndex;
    }

    @Nullable
    public synchronized List<PlankInfo> getPlanks(UUID ropeUUID) {
        return planksByRope.get(ropeUUID);
    }

    private static final double PLANK_SPACING = 1.25;
    private static final double PLANK_VERTICAL_OFFSET = 0.06;

    public static double computeRopeLength(ObjectList<Vector3d> points) {
        double length = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            var p0 = points.get(i);
            var p1 = points.get(i + 1);
            double dx = p1.x() - p0.x();
            double dy = p1.y() - p0.y();
            double dz = p1.z() - p0.z();
            length += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return length;
    }

    public static int computeMaxPlanks(ObjectList<Vector3d> points) {
        return Math.max(1, (int) (computeRopeLength(points) / PLANK_SPACING));
    }

    public static double[] computePlankPosition(ObjectList<Vector3d> points, int plankIndex, double collisionRadius) {
        double ropeLength = computeRopeLength(points);
        int totalPlanks = Math.max(1, (int) (ropeLength / PLANK_SPACING));
        double t = (plankIndex + 0.5) / totalPlanks;
        double targetDist = t * ropeLength;

        double accumulated = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            var p0 = points.get(i);
            var p1 = points.get(i + 1);
            double dx = p1.x() - p0.x();
            double dy = p1.y() - p0.y();
            double dz = p1.z() - p0.z();
            double segLen = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (segLen < 1e-10) continue;
            if (accumulated + segLen >= targetDist) {
                double frac = (targetDist - accumulated) / segLen;
                double mx = p0.x() + dx * frac;
                double my = p0.y() + dy * frac;
                double mz = p0.z() + dz * frac;
                return new double[]{mx, my + collisionRadius + PLANK_VERTICAL_OFFSET, mz, (double) i};
            }
            accumulated += segLen;
        }

        var last = points.get(points.size() - 1);
        return new double[]{last.x(), last.y() + collisionRadius + PLANK_VERTICAL_OFFSET, last.z(), (double) (points.size() - 2)};
    }

    public static void cleanupDeadEntries(ServerLevel level) {
        var manager = get(level);
        var ropeManager = ServerLevelRopeManager.getOrCreate(level);
        if (ropeManager == null) return;
        boolean dirty = false;
        var iter = manager.planksByRope.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            UUID ropeUUID = entry.getKey();
            var strand = ropeManager.getStrand(ropeUUID);
            if (strand == null) {
                ModLog.debug(BRIDGE, "Cleaning up dead rope {} (strand no longer exists)", ropeUUID);
                iter.remove();
                dirty = true;
            }
        }
        if (dirty) manager.setDirty();
    }


    public static void removePlankBySubLevel(ServerLevel level, UUID subLevelUUID) {
        var manager = get(level);
        boolean dirty = false;
        for (var entry : manager.planksByRope.entrySet()) {
            var removed = entry.getValue().removeIf(info -> info.subLevelUUID().equals(subLevelUUID));
            if (removed) {
                ModLog.debug(BRIDGE, "Removed plank entry for subLevel {} from rope {}", subLevelUUID, entry.getKey());
                dirty = true;
            }
        }
        if (dirty) manager.setDirty();
    }

    public static void onRopeDestroyed(ServerLevel level, UUID ropeUUID) {
        var manager = get(level);
        var planks = manager.planksByRope.get(ropeUUID);
        ModLog.warn(BRIDGE, "onRopeDestroyed called for ropeUUID={}, planks found={}", ropeUUID, planks != null ? planks.size() : 0);
        if (planks == null) {
            ModLog.warn(BRIDGE, "onRopeDestroyed: no planks found for ropeUUID={}, returning", ropeUUID);
            return;
        }

        var container = (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
        if (container != null) {
            for (PlankInfo info : planks) {
                var subLevel = container.getSubLevel(info.subLevelUUID());
                ModLog.warn(BRIDGE, "onRopeDestroyed: plank idx={}, subLevelUUID={}, found={}, name before={}",
                        info.plankIndex(), info.subLevelUUID(), subLevel != null,
                        subLevel instanceof ServerSubLevel ssl ? ssl.getName() : "N/A");
                if (subLevel instanceof ServerSubLevel ssl) {
                    ssl.setName(null);
                    ModLog.warn(BRIDGE, "onRopeDestroyed: cleared name for subLevelUUID={}", info.subLevelUUID());
                }
            }
        } else {
            ModLog.warn(BRIDGE, "onRopeDestroyed: container was null, could not clear names");
        }

        manager.planksByRope.remove(ropeUUID);
        manager.setDirty();
        ModLog.warn(BRIDGE, "onRopeDestroyed: removed rope entry for {}, map now has {} entries", ropeUUID, manager.planksByRope.size());
    }

    public static boolean isBridgePlankSubLevel(Level level, BlockPos pos) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        boolean result = subLevel != null && subLevel.getName() != null && subLevel.getName().startsWith("bridge_plank_");
        if (result) {
            ModLog.warn(BRIDGE, "isBridgePlankSubLevel=true at {} subLevel={} name='{}' isServer={}",
                    pos, subLevel.getUniqueId(), subLevel.getName(), level instanceof ServerLevel);
        }
        return result;
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getFace() == null) return;
        Level level = event.getLevel();
        BlockPos placePos = event.getPos().relative(event.getFace());
        if (isBridgePlankSubLevel(level, placePos)) {
            ModLog.warn(BRIDGE, "onRightClickBlock CANCELLING at {} on side {}", placePos, level.isClientSide ? "CLIENT" : "SERVER");
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.PASS);
            if (event.getEntity() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("bridge.aeronauticsdiscovery.cannot_place"), true);
            }
        }
    }

    public static void teleportAllPlanks(ServerLevel level) {
        var manager = get(level);
        ModLog.trace(BRIDGE, "teleportAllPlanks called, ropes in map: {}", manager.planksByRope.size());

        var container = (ServerSubLevelContainer)
                SubLevelContainer.getContainer(level);
        if (container == null) {
            ModLog.debug(BRIDGE, "No container");
            return;
        }
        var physicsSystem = container.physicsSystem();
        if (physicsSystem == null) {
            ModLog.debug(BRIDGE, "No physicsSystem");
            return;
        }
        var ropeManager = ServerLevelRopeManager.getOrCreate(level);
        if (ropeManager == null) {
            ModLog.debug(BRIDGE, "No ropeManager");
            return;
        }

        var iter = manager.planksByRope.entrySet().iterator();
        while (iter.hasNext()) {
            var ropeEntry = iter.next();
            UUID ropeUUID = ropeEntry.getKey();
            var strand = ropeManager.getStrand(ropeUUID);
            if (strand == null) {
                continue;
            }
            if (!strand.isActive()) {
                continue;
            }

            var points = strand.getPoints();
            ModLog.trace(BRIDGE, "Rope {} has {} points, {} planks", ropeUUID, points.size(), ropeEntry.getValue().size());

            int maxPlanks = computeMaxPlanks(points);
            var planks = ropeEntry.getValue();
            var plankIter = planks.iterator();
            while (plankIter.hasNext()) {
                PlankInfo info = plankIter.next();

                if (info.plankIndex() >= maxPlanks) {
                    ModLog.debug(BRIDGE, "Dropping excess plank idx={} (maxPlanks={}) rope={}", info.plankIndex(), maxPlanks, ropeUUID);
                    var subLevel = container.getSubLevel(info.subLevelUUID());
                    if (subLevel instanceof ServerSubLevel ssl) {
                        ssl.setName(null);
                    }
                    plankIter.remove();
                    manager.setDirty();
                    continue;
                }

                var pos = computePlankPosition(points, info.plankIndex(), strand.getCollisionRadius());
                double mx = pos[0], my = pos[1], mz = pos[2];
                int segIdx = (int) pos[3];

                var subLevel = container.getSubLevel(info.subLevelUUID());
                if (!(subLevel instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ssl)) {
                    ModLog.trace(BRIDGE, "  SubLevel {} not loaded yet, skipping", info.subLevelUUID());
                    continue;
                }

                if (Config.planksLevelled) {
                    BridgeUtility.setYawOrientation(ssl.logicalPose().orientation(), points.get(segIdx), points.get(segIdx + 1));
                }
                ssl.logicalPose().position().set(mx, my, mz);
                ssl.updateLastPose();

                var pipeline = physicsSystem.getPipeline();
                pipeline.teleport(ssl,
                        ssl.logicalPose().position(),
                        ssl.logicalPose().orientation());
                pipeline.resetVelocity(ssl);
            }

            if (planks.isEmpty()) {
                ModLog.debug(BRIDGE, "Rope {} has no remaining planks, removing", ropeUUID);
                iter.remove();
                manager.setDirty();
            }
        }


    }
}
