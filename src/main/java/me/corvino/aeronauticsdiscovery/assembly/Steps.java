package me.corvino.aeronauticsdiscovery.assembly;

import me.corvino.aeronauticsdiscovery.assembly.steps.*;

public final class Steps {
    private Steps() {}

    public static final AssemblyStep LOAD_TEMPLATE       = new LoadTemplateStep();
    public static final AssemblyStep PLACE_BLOCKS        = new PlaceBlocksStep();
    public static final AssemblyStep FIND_ASSEMBLY_START = new FindAssemblyStartStep();
    public static final AssemblyStep READINESS_CHECK     = new ReadinessCheckStep();
    public static final AssemblyStep ASSEMBLE            = new AssembleStep();
    public static final AssemblyStep POPULATE_SEATS      = new PopulateSeatsStep();
}
