package me.corvino.aeronauticsdiscovery.autopilot.goals;

import me.corvino.aeronauticsdiscovery.autopilot.AutopilotBias;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotContext;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoal;
import me.corvino.aeronauticsdiscovery.autopilot.GoalCategory;

/**
 * Produces no bias of its own. Level flight is the stabilizer's baseline: it exists purely to
 * declare the flight path and block contradictory goals.
 */
public class StraightFlightGoal implements AutopilotGoal {

    @Override
    public GoalCategory category() {
        return GoalCategory.FLIGHT_PATH;
    }

    @Override
    public AutopilotBias bias(AutopilotContext context) {
        return AutopilotBias.NONE;
    }
}
