package me.corvino.aeronauticsdiscovery.patrol;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.PATROL;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keyed by {@code structure id -> packed start chunk position}. The "rejected by weight" case is
 * recorded as {@code targetStructure -> chunk} too, so a failed weight roll is final for that instance.
 */
public class PatrolManager extends SavedData {

    private static final String DATA_NAME = CreateAeronauticsDiscovery.MODID + "_patrols";
    private static final String TAG_KEY = "Patrols";
    private static final String TAG_STRUCTURE = "Structure";
    private static final String TAG_CHUNKS = "Chunks";

    /** structure id (as written in the patrol config target) -> packed start chunk positions. */
    private final Map<String, Set<Long>> handled = new HashMap<>();

    private ServerLevel level;

    public PatrolManager() {
        this(null);
    }

    public PatrolManager(ServerLevel level) {
        this.level = level;
    }

    public static PatrolManager get(ServerLevel level) {
        PatrolManager manager = level.getDataStorage().computeIfAbsent(
                new Factory<>(PatrolManager::new, (tag, provider) -> load(tag), null),
                DATA_NAME);
        manager.level = level;
        return manager;
    }

    /**
     * @return true if the given structure instance was already processed (spawned or rejected).
     */
    public boolean isHandled(String targetStructure, long packedStartChunk) {
        Set<Long> chunks = handled.get(targetStructure);
        return chunks != null && chunks.contains(packedStartChunk);
    }

    /** Marks the given structure instance as processed and persists the change. */
    public void markHandled(String targetStructure, long packedStartChunk) {
        handled.computeIfAbsent(targetStructure, k -> new HashSet<>()).add(packedStartChunk);
        setDirty();
        ModLog.debug(PATROL, "Marked {} instance @ chunk {} as handled",
                targetStructure, packedStartChunk);
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Set<Long>> entry : handled.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(TAG_STRUCTURE, entry.getKey());
            entryTag.put(TAG_CHUNKS, new LongArrayTag(entry.getValue().stream().mapToLong(Long::longValue).toArray()));
            list.add(entryTag);
        }
        tag.put(TAG_KEY, list);
        return tag;
    }

    private static PatrolManager load(CompoundTag tag) {
        PatrolManager manager = new PatrolManager();
        if (tag.contains(TAG_KEY, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                String structure = entry.getString(TAG_STRUCTURE);
                LongArrayTag chunks = (LongArrayTag) entry.get(TAG_CHUNKS);
                if (chunks == null) continue;
                Set<Long> set = new HashSet<>();
                for (long chunk : chunks.getAsLongArray()) {
                    set.add(chunk);
                }
                manager.handled.put(structure, set);
            }
        }
        return manager;
    }

    public Map<String, Set<Long>> getAllHandled() {
        return Map.copyOf(handled);
    }

    // unused accessor kept symmetric with other SavedData managers; level may be null before attach
    @SuppressWarnings("unused")
    private ServerLevel level() {
        return level;
    }
}
