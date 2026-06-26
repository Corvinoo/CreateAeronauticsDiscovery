package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.assembly.steps.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class Pipelines {
    private static final Map<String, AssemblyPipeline> REGISTRY = new HashMap<>();

    //TODO: replace static delays with contextual ones from steps themselves
    public static final AssemblyPipeline FLYOVER = register(new AssemblyPipeline("flyover", List.of(
            step(LoadTemplateStep::new, 2),
            step(LoadChunkStep::new, 200), //TODO this must use ChunkLoad events to be able to be deferred regardless of the machine
            step(PlaceBlocksStep::new, 0),
            step(FindAssemblyStartStep::new, 0),
            step(ReadinessCheckStep::new, 0),
            step(AssembleStep::new, 0), // TODO rotation and placement should have a step that overrides the "afterAssembly" branch, meaning that I can rotate the structure way before and make it look nice
            step(AddForceLoadTicketStep::new, 0),
            step(CleanUpItemEntities::new, 0),
            step(PopulateSeatsStep::new, 0),
            step(UnloadChunkStep::new, 0)
    )));

    public static final AssemblyPipeline WORLDGEN = register(new AssemblyPipeline("worldgen", List.of(
            step(LoadTemplateStep::new),
            step(ReadinessCheckStep::new),
            step(AssembleStep::new)
    )));

    public static final AssemblyPipeline COMMAND = register(new AssemblyPipeline("command", List.of(
            step(LoadTemplateStep::new, 2),
            step(LoadChunkStep::new, 200), //TODO this must use ChunkLoad events to be able to be deferred regardless of the machine
            step(PlaceBlocksStep::new, 0),
            step(FindAssemblyStartStep::new, 0),
            step(ReadinessCheckStep::new, 0),
            step(AssembleStep::new, 0),
            step(AddForceLoadTicketStep::new, 0),
            step(CleanUpItemEntities::new, 0),
            step(PopulateSeatsStep::new, 0),
            step(UnloadChunkStep::new, 0)
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

    public static AssemblyPipelineEntry step(Supplier<AssemblyStep> stepFactory) {
        var step = stepFactory.get();
        return new AssemblyPipelineEntry(step, 0);
    }

    public static AssemblyPipelineEntry step(Supplier<AssemblyStep> stepFactory, long withDelay) {
        var step = stepFactory.get();
        return new AssemblyPipelineEntry(step, withDelay);
    }
}
