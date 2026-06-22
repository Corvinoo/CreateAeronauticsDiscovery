package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.assembly.steps.*;
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
    void flyoverPipelineHasAllSteps() {
        AssemblyPipeline pipeline = Pipelines.byName("flyover");
        assertEquals(6, pipeline.steps().size());
    }

    @Test
    void worldgenPipelineIsRegistered() {
        AssemblyPipeline pipeline = Pipelines.byName("worldgen");
        assertNotNull(pipeline);
        assertEquals("worldgen", pipeline.name());
    }

    @Test
    void worldgenPipelineHasCorrectSteps() {
        AssemblyPipeline pipeline = Pipelines.byName("worldgen");
        assertEquals(3, pipeline.steps().size());
    }

    @Test
    void commandPipelineIsRegistered() {
        AssemblyPipeline pipeline = Pipelines.byName("command");
        assertNotNull(pipeline);
        assertEquals("command", pipeline.name());
    }

    @Test
    void commandPipelineHasCorrectSteps() {
        AssemblyPipeline pipeline = Pipelines.byName("command");
        assertEquals(6, pipeline.steps().size());
    }

    @Test
    void byNameThrowsForUnknown() {
        assertThrows(IllegalArgumentException.class, () -> Pipelines.byName("nonexistent"));
    }

    @Test
    void byNameThrowsForNull() {
        assertThrows(IllegalArgumentException.class, () -> Pipelines.byName(null));
    }

    @Test
    void flyoverPipelineContainsAllExpectedStepTypes() {
        AssemblyPipeline pipeline = Pipelines.byName("flyover");
        Class<?>[] expectedTypes = {
                LoadTemplateStep.class,
                PlaceBlocksStep.class,
                FindAssemblyStartStep.class,
                ReadinessCheckStep.class,
                AssembleStep.class,
                PopulateSeatsStep.class,
        };
        for (int i = 0; i < expectedTypes.length; i++) {
            assertTrue(expectedTypes[i].isInstance(pipeline.steps().get(i)),
                    "Step " + i + " should be " + expectedTypes[i].getSimpleName()
                            + " but was " + pipeline.steps().get(i).getClass().getSimpleName());
        }
    }

    @Test
    void worldgenPipelineContainsExpectedStepTypes() {
        AssemblyPipeline pipeline = Pipelines.byName("worldgen");
        Class<?>[] expectedTypes = {
                LoadTemplateStep.class,
                ReadinessCheckStep.class,
                AssembleStep.class,
        };
        for (int i = 0; i < expectedTypes.length; i++) {
            assertTrue(expectedTypes[i].isInstance(pipeline.steps().get(i)),
                    "Step " + i + " should be " + expectedTypes[i].getSimpleName()
                            + " but was " + pipeline.steps().get(i).getClass().getSimpleName());
        }
    }

    @Test
    void commandPipelineContainsExpectedStepTypes() {
        AssemblyPipeline pipeline = Pipelines.byName("command");
        Class<?>[] expectedTypes = {
                LoadTemplateStep.class,
                PlaceBlocksStep.class,
                FindAssemblyStartStep.class,
                ReadinessCheckStep.class,
                AssembleStep.class,
                PopulateSeatsStep.class,
        };
        for (int i = 0; i < expectedTypes.length; i++) {
            assertTrue(expectedTypes[i].isInstance(pipeline.steps().get(i)),
                    "Step " + i + " should be " + expectedTypes[i].getSimpleName()
                            + " but was " + pipeline.steps().get(i).getClass().getSimpleName());
        }
    }
}
