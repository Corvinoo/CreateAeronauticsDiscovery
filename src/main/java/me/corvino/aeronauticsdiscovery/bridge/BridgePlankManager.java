package me.corvino.aeronauticsdiscovery.bridge;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
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

    @javax.annotation.Nullable
    public synchronized List<PlankInfo> getPlanks(UUID ropeUUID) {
        return planksByRope.get(ropeUUID);
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aeronauticsdiscovery.BridgePlankManager");

    public static void teleportAllPlanks(ServerLevel level) {
        var manager = get(level);
        LOG.info("teleportAllPlanks called, ropes in map: {}", manager.planksByRope.size());

        var container = (dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer)
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) {
            LOG.info("No container");
            return;
        }
        var physicsSystem = container.physicsSystem();
        if (physicsSystem == null) {
            LOG.info("No physicsSystem");
            return;
        }
        var ropeManager = dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager.getOrCreate(level);
        if (ropeManager == null) {
            LOG.info("No ropeManager");
            return;
        }

        for (var ropeEntry : manager.planksByRope.entrySet()) {
            UUID ropeUUID = ropeEntry.getKey();
            var strand = ropeManager.getStrand(ropeUUID);
            if (strand == null || !strand.isActive()) {
                LOG.info("Strand {} not found or inactive", ropeUUID);
                continue;
            }

            var points = strand.getPoints();
            LOG.info("Rope {} has {} points, {} planks", ropeUUID, points.size(), ropeEntry.getValue().size());

            for (PlankInfo info : ropeEntry.getValue()) {
                int segIdx = info.segmentIndex() + 1;
                if (segIdx < 1 || segIdx + 1 >= points.size()) {
                    LOG.info("  Skipping segIdx={}, points.size={}", segIdx, points.size());
                    continue;
                }

                var p0 = points.get(segIdx);
                var p1 = points.get(segIdx + 1);

                double mx = (p0.x() + p1.x()) / 2.0;
                double my = (p0.y() + p1.y()) / 2.0 + 0.03;
                double mz = (p0.z() + p1.z()) / 2.0;

                var subLevel = container.getSubLevel(info.subLevelUUID());
                if (!(subLevel instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ssl)) {
                    LOG.info("  SubLevel {} not found in container", info.subLevelUUID());
                    continue;
                }

                ssl.logicalPose().position().set(mx, my, mz);
                ssl.updateLastPose();

                var pipeline = physicsSystem.getPipeline();
                pipeline.teleport(ssl,
                        ssl.logicalPose().position(),
                        ssl.logicalPose().orientation());

//                LOG.info("  Teleported slab to midpoint ({},{},{})", mx, my, mz);
            }
        }
    }
}
