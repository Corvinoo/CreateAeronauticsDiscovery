package me.corvino.aeronauticsdiscovery.assembly;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipelinesTest {

    @Test
    void flyoverPipelineIsRegistered() {
        AssemblyPipeline pipeline = Pipelines.byName("flyover");
        assertNotNull(pipeline);
        assertEquals("flyover", pipeline.name());
    }
    
    @Test
    void worldgenPipelineIsRegistered() {
        AssemblyPipeline pipeline = Pipelines.byName("worldgen");
        assertNotNull(pipeline);
        assertEquals("worldgen", pipeline.name());
    }
    

    @Test
    void commandPipelineIsRegistered() {
        AssemblyPipeline pipeline = Pipelines.byName("command");
        assertNotNull(pipeline);
        assertEquals("command", pipeline.name());
    }
    

    @Test
    void byNameThrowsForUnknown() {
        assertThrows(IllegalArgumentException.class, () -> Pipelines.byName("nonexistent"));
    }

    @Test
    void byNameThrowsForNull() {
        assertThrows(IllegalArgumentException.class, () -> Pipelines.byName(null));
    }
}
