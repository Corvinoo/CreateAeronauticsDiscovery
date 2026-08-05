package me.corvino.aeronauticsdiscovery.autopilot;

/**
 * Where a craft should be placed relative to a spawn anchor (a structure's located position).
 * Goals that need a specific placement relative to a point provide one of these; goals that don't care provide
 * {@link #NONE} and the craft spawns at the anchor itself.
 *
 * @param offsetX    horizontal X offset from the anchor, in blocks
 * @param offsetZ    horizontal Z offset from the anchor, in blocks
 * @param yawRadians heading in radians (0 = north, matching the craft nose at rotation NONE)
 */
public record SpawnPlacement(int offsetX, int offsetZ, double yawRadians) {

    public static final SpawnPlacement NONE = new SpawnPlacement(0, 0, 0.0);

    public boolean isNone() {
        return this.equals(NONE);
    }
}
