package me.corvino.aeronauticsdiscovery.bridge;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Map;
import java.util.UUID;

public class BridgePlankManager extends SavedData {

    private static final String DATA_NAME = CreateAeronauticsDiscovery.MODID + "_bridge_planks";

    private final Map<UUID, BitSet> planksByRope = new Object2ObjectOpenHashMap<>();

    public static SavedData.Factory<BridgePlankManager> factory() {
        return new SavedData.Factory<>(BridgePlankManager::new, BridgePlankManager::load);
    }

    public static BridgePlankManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static BridgePlankManager load(CompoundTag tag, HolderLookup.Provider registries) {
        BridgePlankManager manager = new BridgePlankManager();
        if (tag.contains("planks")) {
            manager.planksByRope.clear();
            CompoundTag planksTag = tag.getCompound("planks");
            for (String key : planksTag.getAllKeys()) {
                UUID ropeUUID = UUID.fromString(key);
                byte[] bytes = planksTag.getByteArray(key);
                BitSet bitSet = BitSet.valueOf(bytes);
                manager.planksByRope.put(ropeUUID, bitSet);
            }
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag planksTag = new CompoundTag();
        for (Map.Entry<UUID, BitSet> entry : planksByRope.entrySet()) {
            planksTag.putByteArray(entry.getKey().toString(), entry.getValue().toByteArray());
        }
        tag.put("planks", planksTag);
        return tag;
    }

    public synchronized int addPlank(ServerLevel level, UUID ropeUUID, int maxPlanks) {
        BitSet bitSet = planksByRope.computeIfAbsent(ropeUUID, k -> new BitSet());
        for (int i = 0; i < maxPlanks; i++) {
            if (!bitSet.get(i)) {
                bitSet.set(i);
                setDirty();
                return i;
            }
        }
        return -1;
    }

    public synchronized boolean removePlank(UUID ropeUUID, int segmentIndex) {
        BitSet bitSet = planksByRope.get(ropeUUID);
        if (bitSet != null && bitSet.get(segmentIndex)) {
            bitSet.clear(segmentIndex);
            if (bitSet.isEmpty()) {
                planksByRope.remove(ropeUUID);
            }
            setDirty();
            return true;
        }
        return false;
    }

    public synchronized boolean hasPlank(UUID ropeUUID, int segmentIndex) {
        BitSet bitSet = planksByRope.get(ropeUUID);
        return bitSet != null && bitSet.get(segmentIndex);
    }

    @Nullable
    public synchronized BitSet getPlanks(UUID ropeUUID) {
        BitSet bitSet = planksByRope.get(ropeUUID);
        return bitSet != null ? (BitSet) bitSet.clone() : null;
    }

    public synchronized void removeAll(UUID ropeUUID) {
        planksByRope.remove(ropeUUID);
        setDirty();
    }
}
