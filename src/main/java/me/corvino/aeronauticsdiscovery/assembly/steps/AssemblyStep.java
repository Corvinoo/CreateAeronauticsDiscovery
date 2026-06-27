package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import org.jetbrains.annotations.TestOnly;

import java.util.Random;

public interface AssemblyStep {
    AssemblyResult run(AssemblyContext ctx);

    default void cleanup(AssemblyContext ctx) {}

    @TestOnly
    static AssemblyResult failRandomly(){
        var random = new Random().nextInt(3);
        if(random < 2) return AssemblyResult.FAIL;
        return AssemblyResult.SUCCESS;
    }
}

