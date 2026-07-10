package me.corvino.aeronauticsdiscovery.assembly.queue;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyPipeline;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.benchmark.PrefabBenchmark;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Queues and drives {@link AssemblyContext}s through their {@link AssemblyPipeline}
 * to completion, one queue per level, ticked every {@link LevelTickEvent.Post}.
 *
 * <p>Entries are retried until they succeed, fail past {@code maxRetries}, or
 * are still {@code WAITING} on their next eligible tick.</p>
 */
public class AssemblyQueue extends SavedData {

    private static final String DATA_NAME = CreateAeronauticsDiscovery.MODID + "_assembly_queue";

    private final List<Entry> entries = new ArrayList<>();

    /**
     * Entries enqueued while {@link #processing} is true are buffered here and
     * flushed after iteration completes, so {@link #enqueue} is always safe to
     * call re-entrantly (e.g. from a pipeline step's own {@code execute}).
     */
    private final List<Entry> pendingAdd = new ArrayList<>();
    private boolean processing = false;

    public record Entry(
            ResourceLocation templateId,
            AssemblyPipeline pipeline,
            AssemblyContext context,
            int retryCount
    ) {
        public Entry withRetryCount(int newCount) {
            return new Entry(templateId, pipeline, context, newCount);
        }
    }


    public static SavedData.Factory<AssemblyQueue> factory() {
        return new SavedData.Factory<>(AssemblyQueue::new, AssemblyQueue::load);
    }

    public static AssemblyQueue get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            get(level).process(level);
        }
    }


    public List<Entry> getEntries() {
        return List.copyOf(entries);
    }

    public void enqueue(AssemblyPipeline pipeline, AssemblyContext ctx) {
        Entry entry = new Entry(ctx.templateId, pipeline, ctx, 0);
        if (processing) {
            pendingAdd.add(entry);
        } else {
            entries.add(entry);
        }
        setDirty();
    }

    private void process(ServerLevel level) {
        if (entries.isEmpty() && pendingAdd.isEmpty()) return;

        long startNanos = System.nanoTime();
        int beforeCount = entries.size();
        long currentTick = level.getGameTime();

        processing = true;
        try {
            processEntries(level, currentTick);
        } finally {
            processing = false;
            flushPendingAdds();
        }

        if (PrefabBenchmark.isActive()) {
            PrefabBenchmark.recordTick(System.nanoTime() - startNanos, beforeCount, entries.size());
        }
    }

    private void processEntries(ServerLevel level, long currentTick) {
        ListIterator<Entry> it = entries.listIterator();
        while (it.hasNext()) {
            processEntry(level, currentTick, it, it.next());
        }
    }

    private void flushPendingAdds() {
        if (pendingAdd.isEmpty()) return;
        entries.addAll(pendingAdd);
        pendingAdd.clear();
        setDirty();
    }

    // Tick - per-entry decision tree
    private void processEntry(ServerLevel level, long currentTick, ListIterator<Entry> it, Entry entry) {
        AssemblyContext ctx = entry.context();
        ctx.level = level;

        if (entry.retryCount() >= ctx.maxRetries) {
            discardEntry(it, entry);
            return;
        }

        AssemblyResult result = entry.pipeline().execute(ctx, currentTick);
        applyResult(level, it, entry, ctx, result);
    }

    private void discardEntry(ListIterator<Entry> it, Entry entry) {
        CreateAeronauticsDiscovery.LOGGER.warn(
                "[QUEUE] Discarding '{}' (src={}) after {} failed attempts",
                entry.templateId(), entry.context().source, entry.retryCount());
        it.remove();
        setDirty();
    }

    private void applyResult(ServerLevel level, ListIterator<Entry> it, Entry entry, AssemblyContext ctx, AssemblyResult result) {
        switch (result) {
            case SUCCESS -> {
                CreateAeronauticsDiscovery.LOGGER.debug("[QUEUE] SUCCESS: '{}' (src={})", ctx.templateId, ctx.source);
                PostAssemblyFinalizer.run(level, ctx);
                it.remove();
                setDirty();
            }
            case FAIL -> {
                CreateAeronauticsDiscovery.LOGGER.warn("[QUEUE] FAIL: '{}' (src={}, attempt {}/{})",
                        ctx.templateId, ctx.source, entry.retryCount() + 1, ctx.maxRetries);
                it.set(entry.withRetryCount(entry.retryCount() + 1));
                setDirty();
            }
            case WAITING -> {}
        }
    }

    // Serialization
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        entries.forEach(entry -> list.add(AssemblyEntrySerializer.save(entry)));
        tag.put("Entries", list);
        return tag;
    }

    private static AssemblyQueue load(CompoundTag tag, HolderLookup.Provider provider) {
        AssemblyQueue data = new AssemblyQueue();
        ListTag list = tag.getList("Entries", 10);
        for (int i = 0; i < list.size(); i++) {
            AssemblyEntrySerializer.load(list.getCompound(i)).ifPresent(data.entries::add);
        }
        return data;
    }
}