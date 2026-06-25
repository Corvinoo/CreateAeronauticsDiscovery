package me.corvino.aeronauticsdiscovery.assembly;

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