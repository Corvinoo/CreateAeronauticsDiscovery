package me.corvino.aeronauticsdiscovery.assembly;

public interface AssemblyStep {
    AssemblyResult run(AssemblyContext ctx);

    default void cleanup(AssemblyContext ctx) {}
}