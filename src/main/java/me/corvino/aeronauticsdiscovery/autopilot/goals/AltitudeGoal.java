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

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Maintains an altitude band: pitches the craft up while it is below {@link #minAltitude()} and
 * pitches it down while it is above {@link #maxAltitude()} (when configured), with the bias scaled
 * by how far outside the band it is. Composes with any {@link GoalCategory#FLIGHT_PATH} goal.
 * Parameterized entirely via its datapack codec.
 *
 * @param minAltitude lower bound of the band in world-space blocks
 * @param maxAltitude upper bound of the band, or {@code null} for no ceiling
 */
public record AltitudeGoal(double minAltitude, @Nullable Double maxAltitude) implements AutopilotGoal<AltitudeGoal> {

    private static final double ALTITUDE_BIAS_PER_BLOCK = Math.toRadians(0.4);
    private static final double MAX_ALTITUDE_BIAS = Math.toRadians(12);

    public static final AutopilotGoalType<AltitudeGoal> TYPE = AutopilotGoalTypes.<AltitudeGoal>register("altitude",
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.DOUBLE.fieldOf("min_altitude").forGetter(AltitudeGoal::minAltitude),
                    Codec.DOUBLE.optionalFieldOf("max_altitude").forGetter(goal -> Optional.ofNullable(goal.maxAltitude()))
            ).apply(instance, (min, max) -> new AltitudeGoal(min, max.orElse(null)))));

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
        double altitude = context.worldAltitude();
        double below = minAltitude - altitude;
        if (below > 0) {
            double bias = Mth.clamp(below * ALTITUDE_BIAS_PER_BLOCK, 0, MAX_ALTITUDE_BIAS);
            return new AutopilotBias(bias, 0);
        }
        if (maxAltitude != null) {
            double above = altitude - maxAltitude;
            if (above > 0) {
                double bias = -Mth.clamp(above * ALTITUDE_BIAS_PER_BLOCK, 0, MAX_ALTITUDE_BIAS);
                return new AutopilotBias(bias, 0);
            }
        }
        return AutopilotBias.NONE;
    }
}
