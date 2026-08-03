package me.corvino.aeronauticsdiscovery.autopilot.goals;

import com.mojang.serialization.MapCodec;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotBias;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotContext;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoal;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoalType;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoalTypes;
import me.corvino.aeronauticsdiscovery.autopilot.GoalCategory;

/**
 * Produces no bias of its own. Level flight is the stabilizer's baseline: it exists purely to
 * declare the flight path and block contradictory goals.
 */
public final class StraightFlightGoal implements AutopilotGoal<StraightFlightGoal> {

    public static final StraightFlightGoal INSTANCE = new StraightFlightGoal();

    public static final AutopilotGoalType<StraightFlightGoal> TYPE =
            AutopilotGoalTypes.<StraightFlightGoal>register("straight", MapCodec.unit(INSTANCE));

    private StraightFlightGoal() {
    }

    @Override
    public AutopilotGoalType<StraightFlightGoal> type() {
        return TYPE;
    }

    @Override
    public GoalCategory category() {
        return GoalCategory.FLIGHT_PATH;
    }

    @Override
    public AutopilotBias bias(AutopilotContext context) {
        return AutopilotBias.NONE;
    }

    @Override
    public String toString() {
        return "StraightFlightGoal";
    }
}
