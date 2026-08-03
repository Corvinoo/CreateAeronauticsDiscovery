package me.corvino.aeronauticsdiscovery.autopilot.goals;

import me.corvino.aeronauticsdiscovery.autopilot.AutopilotBias;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotContext;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoal;
import me.corvino.aeronauticsdiscovery.autopilot.GoalCategory;
import net.minecraft.util.Mth;

/**
 * Maintains a minimum altitude: pitches the craft up while it is below {@link #getMinAltitude()},
 * with the bias scaled by how far below it is. Composes with any {@link GoalCategory#FLIGHT_PATH}
 * goal.
 */
public class AltitudeGoal implements AutopilotGoal {

    private static final double ALTITUDE_BIAS_PER_BLOCK = Math.toRadians(0.4);
    private static final double MAX_ALTITUDE_BIAS = Math.toRadians(12);

    private double minAltitude;

    public AltitudeGoal(double minAltitude) {
        this.minAltitude = minAltitude;
    }

    @Override
    public GoalCategory category() {
        return GoalCategory.ALTITUDE;
    }

    @Override
    public AutopilotBias bias(AutopilotContext context) {
        double deficit = minAltitude - context.worldAltitude();
        if (deficit <= 0) return AutopilotBias.NONE;
        double bias = Mth.clamp(deficit * ALTITUDE_BIAS_PER_BLOCK, 0, MAX_ALTITUDE_BIAS);
        return new AutopilotBias(bias, 0);
    }

    public double getMinAltitude() {
        return minAltitude;
    }

    public void setMinAltitude(double minAltitude) {
        this.minAltitude = minAltitude;
    }
}
