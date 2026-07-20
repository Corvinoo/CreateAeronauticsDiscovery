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

    public record PlankInfo(int segmentIndex, UUID subLevelUUID, BlockState slabState) {}

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
                    int segIdx = entry.getInt("segment_index");
                    UUID slUUID = entry.getUUID("sub_level_uuid");
                    BlockState slabState = Blocks.OAK_SLAB.defaultBlockState();
                    if (entry.contains("slab_block")) {
                        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(entry.getString("slab_block")));
                        if (block != null) slabState = block.defaultBlockState();
                    }
                    infos.add(new PlankInfo(segIdx, slUUID, slabState));
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
                plankEntry.putInt("segment_index", info.segmentIndex());
                plankEntry.putUUID("sub_level_uuid", info.subLevelUUID());
                plankEntry.putString("slab_block", BuiltInRegistries.BLOCK.getKey(info.slabState().getBlock()).toString());
                list.add(plankEntry);
            }
            ropes.put(entry.getKey().toString(), list);
        }
        tag.put("planks_by_rope", ropes);
        return tag;
    }

    public synchronized int addPlank(UUID ropeUUID, UUID subLevelUUID, int segmentIndex, BlockState slabState) {
        var list = planksByRope.computeIfAbsent(ropeUUID, k -> new ArrayList<>());
        list.add(new PlankInfo(segmentIndex, subLevelUUID, slabState));
        setDirty();
        return segmentIndex;
    }

    @Nullable
    public synchronized List<PlankInfo> getPlanks(UUID ropeUUID) {
        return planksByRope.get(ropeUUID);
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aeronauticsdiscovery.BridgePlankManager");

    public static double[] computePlankPosition(ObjectList<Vector3d> points, int plankIndex, double collisionRadius) {
        int M = points.size() - 2;
        int N = M > 3 ? M - 1 : Math.max(1, M);
        double segPos = N <= 1 ? 0 : (double) plankIndex * (M - 1) / (N - 1);
        int seg = (int) Math.floor(segPos);
        double frac = segPos - seg;
        int segIdx = seg + 1;

        var p0_base = points.get(segIdx);
        var p1_base = points.get(segIdx + 1);
        double mx_base = (p0_base.x() + p1_base.x()) / 2.0;
        double my_base = (p0_base.y() + p1_base.y()) / 2.0;
        double mz_base = (p0_base.z() + p1_base.z()) / 2.0;

        double mx, my, mz;
        if (frac <= 1e-10) {
            mx = mx_base; my = my_base; mz = mz_base;
        } else {
            var p0_next = points.get(segIdx + 1);
            var p1_next = points.get(segIdx + 2);
            double mx_next = (p0_next.x() + p1_next.x()) / 2.0;
            double my_next = (p0_next.y() + p1_next.y()) / 2.0;
            double mz_next = (p0_next.z() + p1_next.z()) / 2.0;
            mx = mx_base + (mx_next - mx_base) * frac;
            my = my_base + (my_next - my_base) * frac;
            mz = mz_base + (mz_next - mz_base) * frac;
        }

        return new double[]{mx, my + collisionRadius + 0.06, mz};
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
                LOG.debug("Strand {} not found or inactive, will retry next tick", ropeUUID);
                continue;
            }

            var points = strand.getPoints();
            LOG.trace("Rope {} has {} points, {} planks", ropeUUID, points.size(), ropeEntry.getValue().size());

            var planks = ropeEntry.getValue();
            var plankIter = planks.iterator();
            while (plankIter.hasNext()) {
                PlankInfo info = plankIter.next();
                var pos = computePlankPosition(points, info.segmentIndex(), strand.getCollisionRadius());
                double mx = pos[0], my = pos[1], mz = pos[2];

                var subLevel = container.getSubLevel(info.subLevelUUID());
                if (!(subLevel instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ssl)) {
                    LOG.debug("  SubLevel {} not found, removing orphaned plank", info.subLevelUUID());
                    plankIter.remove();
                    manager.setDirty();
                    continue;
                }

                if (Config.planksLevelled) {
                    ssl.logicalPose().orientation().identity();
                }
                ssl.logicalPose().position().set(mx, my, mz);
                ssl.updateLastPose();

                var pipeline = physicsSystem.getPipeline();
                pipeline.teleport(ssl,
                        ssl.logicalPose().position(),
                        ssl.logicalPose().orientation());
                pipeline.resetVelocity(ssl);
            }

            //todo check if necessary
            if (planks.isEmpty()) {
                LOG.debug("Rope {} has no remaining planks, removing", ropeUUID);
                iter.remove();
                manager.setDirty();
            }
        }
    }
}
