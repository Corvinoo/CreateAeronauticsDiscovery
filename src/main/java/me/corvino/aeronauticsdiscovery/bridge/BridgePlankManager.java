package me.corvino.aeronauticsdiscovery.bridge;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Vector3d;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.registries.BuiltInRegistries;

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

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aeronauticsdiscovery.BridgePlankManager");

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
            if (strand == null || !strand.isActive()) {
                LOG.trace("Cleaning up dead rope {} at save time", ropeUUID);
                iter.remove();
                dirty = true;
            }
        }
        if (dirty) manager.setDirty();
    }

    public static void teleportAllPlanks(ServerLevel level) {
        var manager = get(level);
        LOG.trace("teleportAllPlanks called, ropes in map: {}", manager.planksByRope.size());

        var container = (ServerSubLevelContainer)
                SubLevelContainer.getContainer(level);
        if (container == null) {
            LOG.debug("No container");
            return;
        }
        var physicsSystem = container.physicsSystem();
        if (physicsSystem == null) {
            LOG.debug("No physicsSystem");
            return;
        }
        var ropeManager = ServerLevelRopeManager.getOrCreate(level);
        if (ropeManager == null) {
            LOG.debug("No ropeManager");
            return;
        }

        var iter = manager.planksByRope.entrySet().iterator();
        while (iter.hasNext()) {
            var ropeEntry = iter.next();
            UUID ropeUUID = ropeEntry.getKey();
            var strand = ropeManager.getStrand(ropeUUID);
            if (strand == null || !strand.isActive()) {
                LOG.trace("Strand {} not found or inactive (null={}), will retry next tick", ropeUUID, strand == null);
                continue;
            }

            var points = strand.getPoints();
            LOG.trace("Rope {} has {} points, {} planks", ropeUUID, points.size(), ropeEntry.getValue().size());

            var planks = ropeEntry.getValue();
            var plankIter = planks.iterator();
            while (plankIter.hasNext()) {
                PlankInfo info = plankIter.next();
                var pos = computePlankPosition(points, info.plankIndex(), strand.getCollisionRadius());
                double mx = pos[0], my = pos[1], mz = pos[2];
                int segIdx = (int) pos[3];

                var subLevel = container.getSubLevel(info.subLevelUUID());
                if (!(subLevel instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ssl)) {
                    LOG.debug("  SubLevel {} not found, removing orphaned plank", info.subLevelUUID());
                    plankIter.remove();
                    manager.setDirty();
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
                LOG.debug("Rope {} has no remaining planks, removing", ropeUUID);
                iter.remove();
                manager.setDirty();
            }
        }


    }
}
