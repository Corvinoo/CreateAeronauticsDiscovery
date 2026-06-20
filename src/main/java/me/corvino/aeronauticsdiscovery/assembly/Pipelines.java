package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.assembly.steps.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Pipelines {
    private static final Map<String, AssemblyPipeline> REGISTRY = new HashMap<>();

    public static final AssemblyPipeline STANDARD = register(new AssemblyPipeline("standard", List.of(
            new LoadTemplateStep(),
            new PlaceBlocksStep(),
            new FindAssemblyStartStep(),
            new ReadinessCheckStep(),
            new AssembleStep(),
            new PopulateSeatsStep(),
            new ApplyVelocityStep(),
            new RotateBodyStep(),
            new NameSubLevelStep(),
            new RegisterFlyoverStep()
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
}
