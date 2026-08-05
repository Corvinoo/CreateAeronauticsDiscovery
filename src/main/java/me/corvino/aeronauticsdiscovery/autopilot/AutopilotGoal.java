package me.corvino.aeronauticsdiscovery.autopilot;

import javax.annotation.Nullable;

/**
 * A single autopilot behaviour. Implementations inspect the per-tick {@link AutopilotContext} and
 * produce an {@link AutopilotBias} that nudges the craft's target attitude.
 * <p>
 * Every goal is registered as an {@link AutopilotGoalType} (with a serialization {@code Codec}) so
 * goal sets can be declared externally in datapack JSON instead of being baked into a mob.
 */
public interface AutopilotGoal<T extends AutopilotGoal<T>> {

    /** The registered type of this goal; used to (de)serialize it in a goal set. */
    AutopilotGoalType<T> type();

    /** The category this goal belongs to; used for mutual-exclusion checks. */
    GoalCategory category();

    /**
     * Compute this goal's desired attitude offset for the given tick.
     *
     * @return the bias to apply, or {@code null} if this goal has no opinion this tick.
     */
    @Nullable
    AutopilotBias bias(AutopilotContext context);

    /**
     * Optional spawn placement hint for crafts whose flight plan includes this goal. Goals that
     * need to start in a specific spot relative to their anchor (e.g. an {@code orbit} goal wants
     * to begin on its ring facing the tangent) return a {@link SpawnPlacement}; goals that don't
     * care return {@link SpawnPlacement#NONE} and the craft spawns at the anchor itself.
     */
    default SpawnPlacement spawnPlacement() {
        return SpawnPlacement.NONE;
    }

    default String name() {
        return getClass().getSimpleName();
    }
}
