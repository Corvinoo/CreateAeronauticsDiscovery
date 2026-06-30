package me.corvino.aeronauticsdiscovery.physics;

//probably should convert into configurations
public record BuoyancyStabilizationConfig(
        // Lift must reach at least this fraction over weight before release; 1.0 = exact balance
        double liftSafetyMargin,
        // Consecutive qualifying substeps required before trusting the reading (debounce against jitter)
        int requiredStableSubsteps,
        // Hard ceiling, in seconds of simulated time, before releasing regardless of lift (safety valve)
        double maxHoldSeconds
) {
    public static final BuoyancyStabilizationConfig DEFAULT =
            new BuoyancyStabilizationConfig(1.02, 10, 30.0);
}