package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.assembly.steps.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipelinesTest {

    @Test
    void standardPipelineIsRegistered() {
        AssemblyPipeline pipeline = Pipelines.byName("standard");
        assertNotNull(pipeline);
        assertEquals("standard", pipeline.name());
    }

    @Test
    void standardPipelineHasAllSteps() {
        AssemblyPipeline pipeline = Pipelines.byName("standard");
        assertEquals(10, pipeline.steps().size());
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
    void standardPipelineContainsAllExpectedStepTypes() {
        AssemblyPipeline pipeline = Pipelines.byName("standard");
        Class<?>[] expectedTypes = {
                LoadTemplateStep.class,
                PlaceBlocksStep.class,
                FindAssemblyStartStep.class,
                ReadinessCheckStep.class,
                AssembleStep.class,
                PopulateSeatsStep.class,
                ApplyVelocityStep.class,
                RotateBodyStep.class,
                NameSubLevelStep.class,
                RegisterFlyoverStep.class,
        };
        for (int i = 0; i < expectedTypes.length; i++) {
            assertTrue(expectedTypes[i].isInstance(pipeline.steps().get(i)),
                    "Step " + i + " should be " + expectedTypes[i].getSimpleName()
                            + " but was " + pipeline.steps().get(i).getClass().getSimpleName());
        }
    }
}
