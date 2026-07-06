package me.corvino.aeronauticsdiscovery.marker;

import net.minecraft.world.phys.Vec3;

/**
 * A trigger event flowing through a {@link MarkerNetwork}: something happened at {@code originPlotPos}
 * (in the same plot-local coordinate space as {@link MarkerEntity#bindToSubLevel}), and any behaviour that
 * cares can react - either immediately (it's the origin) or after a network-computed delay (propagation).
 */
public record MarkerTrigger(Kind kind, Vec3 originWorldPos, int chainDepth) {

    public enum Kind {
        PLAYER_PROXIMITY,
        EXTERNAL_FORCE,
        EXPLOSION,
        PROJECTILE,
    }

    public MarkerTrigger withDepth(int newDepth) {
        return new MarkerTrigger(this.kind, this.originWorldPos, newDepth);
    }
}
