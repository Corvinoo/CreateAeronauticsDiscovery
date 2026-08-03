package me.corvino.aeronauticsdiscovery.autopilot.goals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotBias;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotContext;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoal;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoalType;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoalTypes;
import me.corvino.aeronauticsdiscovery.autopilot.GoalCategory;
import net.minecraft.util.Mth;

/**
 * Maintains a minimum altitude: pitches the craft up while it is below {@link #minAltitude()},
 * with the bias scaled by how far below it is. Composes with any {@link GoalCategory#FLIGHT_PATH}
 * goal. Parameterized entirely via its datapack codec.
 *
 * @param minAltitude altitude floor in world-space blocks
 */
public record AltitudeGoal(double minAltitude) implements AutopilotGoal<AltitudeGoal> {

    private static final double ALTITUDE_BIAS_PER_BLOCK = Math.toRadians(0.4);
    private static final double MAX_ALTITUDE_BIAS = Math.toRadians(12);

    public static final AutopilotGoalType<AltitudeGoal> TYPE = AutopilotGoalTypes.<AltitudeGoal>register("altitude",
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.DOUBLE.fieldOf("min_altitude").forGetter(AltitudeGoal::minAltitude)
            ).apply(instance, AltitudeGoal::new)));

    @Override
    public AutopilotGoalType<AltitudeGoal> type() {
        return TYPE;
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
}
