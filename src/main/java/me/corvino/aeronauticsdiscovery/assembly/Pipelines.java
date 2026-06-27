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
            step(LoadTemplateStep::new),
            step(LoadChunkStep::new), //TODO this must use ChunkLoad events to be able to be deferred regardless of the machine
            step(PlaceBlocksStep::new),
            step(FindAssemblyStartStep::new),
            step(ReadinessCheckStep::new),
            step(AssembleStep::new), // TODO rotation and placement should have a step that overrides the "afterAssembly" branch, meaning that I can rotate the structure way before and make it look nice
            step(AddForceLoadTicketStep::new),
            step(CleanUpItemEntities::new),
            step(PopulateSeatsStep::new),
            step(UnloadChunkStep::new)
    )));

    public static final AssemblyPipeline WORLDGEN = register(new AssemblyPipeline("worldgen", List.of(
            step(LoadTemplateStep::new),
            step(ReadinessCheckStep::new),
            step(AssembleStep::new)
    )));

    public static final AssemblyPipeline COMMAND = register(new AssemblyPipeline("command", List.of(
            step(LoadTemplateStep::new),
            step(LoadChunkStep::new), //TODO this must use ChunkLoad events to be able to be deferred regardless of the machine
            step(PlaceBlocksStep::new),
            step(FindAssemblyStartStep::new),
            step(ReadinessCheckStep::new),
            step(AssembleStep::new),
            step(AddForceLoadTicketStep::new),
            step(CleanUpItemEntities::new),
            step(PopulateSeatsStep::new),
            step(UnloadChunkStep::new)
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

    public static AssemblyStep step(Supplier<AssemblyStep> stepFactory) {
        return stepFactory.get();
    }
}
