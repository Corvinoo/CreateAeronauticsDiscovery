package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.assembly.steps.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class Pipelines {
    private static final Map<String, AssemblyPipeline> REGISTRY = new HashMap<>();

    public static final AssemblyPipeline FLYOVER = register(new AssemblyPipeline("flyover", List.of(
            step(LoadTemplateStep::new, 2),
            step(LoadChunkStep::new, 2),
            step(PlaceBlocksStep::new, 2),
            step(FindAssemblyStartStep::new, 2),
            step(ReadinessCheckStep::new, 2),
            step(AssembleStep::new, 2),
            step(CleanUpItemEntities::new, 2),
            step(PopulateSeatsStep::new, 2),
            step(UnloadChunkStep::new, 2)
    )));

    public static final AssemblyPipeline WORLDGEN = register(new AssemblyPipeline("worldgen", List.of(
            step(LoadTemplateStep::new),
            step(ReadinessCheckStep::new),
            step(AssembleStep::new)
    )));

    public static final AssemblyPipeline COMMAND = register(new AssemblyPipeline("command", List.of(
            step(LoadTemplateStep::new, 2),
            step(LoadChunkStep::new, 2),
            step(PlaceBlocksStep::new, 2),
            step(FindAssemblyStartStep::new, 2),
            step(ReadinessCheckStep::new, 2),
            step(AssembleStep::new, 2),
            step(CleanUpItemEntities::new, 2),
            step(PopulateSeatsStep::new, 2),
            step(UnloadChunkStep::new, 2)
    )));

    private Pipelines() {}

    private static AssemblyPipeline register(AssemblyPipeline pipeline) {
        if (REGISTRY.put(pipeline.name(), pipeline) != null) {
            throw new IllegalStateException("Duplicate pipeline registration: " + pipeline.name());
        }
        return pipeline;
    }

    public static AssemblyPipeline byName(String name) {
        AssemblyPipeline pipeline = REGISTRY.get(name);
        if (pipeline == null) {
            throw new IllegalArgumentException("Unknown pipeline: " + name);
        }
        return pipeline;
    }

    public static Map<String, AssemblyPipeline> getAll() {
        return Map.copyOf(REGISTRY);
    }

    private static AssemblyPipelineEntry step(Supplier<AssemblyStep> stepFactory) {
        var step = stepFactory.get();
        return new AssemblyPipelineEntry(step, 0);
    }

    private static AssemblyPipelineEntry step(Supplier<AssemblyStep> stepFactory, long withDelay) {
        var step = stepFactory.get();
        return new AssemblyPipelineEntry(step, withDelay);
    }
}
