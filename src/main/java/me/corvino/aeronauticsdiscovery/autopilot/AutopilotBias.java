package me.corvino.aeronauticsdiscovery.autopilot;

/**
 * Goals return {@link #NONE} when they have no opinion for a given tick; the {@link Autopilot}
 * sums every active goal's bias into a single steering command.
 */
public record AutopilotBias(double pitch, double roll) {

    public static final AutopilotBias NONE = new AutopilotBias(0, 0);

    public AutopilotBias add(AutopilotBias other) {
        return new AutopilotBias(pitch + other.pitch, roll + other.roll);
    }

    public boolean isZero() {
        return pitch == 0 && roll == 0;
    }
}
