package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.assembly.steps.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class Pipelines {
    private static final Map<String, AssemblyPipeline> REGISTRY = new HashMap<>();

    public static final AssemblyPipeline FLYOVER = register(new AssemblyPipeline("flyover", () -> List.of(
            new LoadTemplateStep(),
            new LoadChunkStep(),
            new PlaceBlocksStep(),
            new FindAssemblyStartStep(),
            new ReadinessCheckStep(),
            new AssembleStep(), // TODO rotation and placement should have a step that overrides the "afterAssembly" branch, meaning that I can rotate the structure way before and make it look nice
            new AddForceLoadTicketStep(),
            new CleanUpItemEntities(),
            new PopulateSeatsStep(),
            new UnloadChunkStep()
    )));

    public static final AssemblyPipeline WORLDGEN = register(new AssemblyPipeline("worldgen", () -> List.of(
            new LoadTemplateStep(),
            new ReadinessCheckStep(),
            new AssembleStep()
    )));

    public static final AssemblyPipeline COMMAND = register(new AssemblyPipeline("command", () -> List.of(
            new LoadTemplateStep(),
            new LoadChunkStep(),
            new PlaceBlocksStep(),
            new FindAssemblyStartStep(),
            new ReadinessCheckStep(),
            new AssembleStep(),
            new AddForceLoadTicketStep(),
            new CleanUpItemEntities(),
            new PopulateSeatsStep(),
            new UnloadChunkStep()
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

}
