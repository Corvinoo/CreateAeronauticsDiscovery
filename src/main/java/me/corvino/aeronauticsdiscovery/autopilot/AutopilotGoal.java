package me.corvino.aeronauticsdiscovery.autopilot;

import javax.annotation.Nullable;


public interface AutopilotGoal {

    /** The category this goal belongs to; used for mutual-exclusion checks. */
    GoalCategory category();

    @Nullable
    AutopilotBias bias(AutopilotContext context);

    default String name() {
        return getClass().getSimpleName();
    }
}
