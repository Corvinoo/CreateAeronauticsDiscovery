package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyStep;

public class NameSubLevelStep implements AssemblyStep {
    private final String customName;

    public NameSubLevelStep() {
        this.customName = null;
    }

    public NameSubLevelStep(String customName) {
        this.customName = customName;
    }

    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        if (ctx.assemblyResult == null) return AssemblyResult.SUCCESS;

        String name;
        if (customName != null) {
            name = customName;
        } else {
            name = switch (ctx.source) {
                case FLYOVER -> "flyover";
                default -> ctx.templateId.getPath();
            };
        }

        ctx.assemblyResult.subLevel().setName(name);
        return AssemblyResult.SUCCESS;
    }
}
