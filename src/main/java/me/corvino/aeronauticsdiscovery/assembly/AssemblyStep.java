package me.corvino.aeronauticsdiscovery.assembly;

@FunctionalInterface
public interface AssemblyStep {
    AssemblyResult run(AssemblyContext ctx);
}
