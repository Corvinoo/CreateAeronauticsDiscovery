package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.assembly.steps.AssemblyStep;

public record AssemblyPipelineEntry(
        AssemblyStep step,
        long delayInTicks
) {
}
