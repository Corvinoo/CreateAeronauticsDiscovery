package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyQueue.Entry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyQueueTest {

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.parse("aeronauticsdiscovery:test");

    @Test
    void entryWithRetryCountReturnsNewEntry() {
        AssemblyPipeline pipeline = new AssemblyPipeline("test", List.of());
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.COMMAND).build();
        Entry entry = new Entry(TEMPLATE_ID, pipeline, ctx, 0);

        Entry updated = entry.withRetryCount(5);
        assertEquals(5, updated.retryCount());
        assertEquals(0, entry.retryCount());
        assertSame(entry.templateId(), updated.templateId());
        assertSame(entry.pipeline(), updated.pipeline());
        assertSame(entry.context(), updated.context());
        assertNotSame(entry, updated);
    }

    @Test
    void entryPreservesAllFields() {
        AssemblyPipeline pipeline = new AssemblyPipeline("test", List.of());
        AssemblyContext ctx = AssemblyContext.builder(null, TEMPLATE_ID, AssemblySource.WORLDGEN)
                .activationDistance(64)
                .build();
        Entry entry = new Entry(TEMPLATE_ID, pipeline, ctx, 3);

        assertEquals(TEMPLATE_ID, entry.templateId());
        assertSame(pipeline, entry.pipeline());
        assertSame(ctx, entry.context());
        assertEquals(3, entry.retryCount());
    }
}
