package me.corvino.aeronauticsdiscovery.pin;

import net.minecraft.world.phys.Vec3;


public record PinTrigger(Kind kind, Vec3 originWorldPos) {

    /**
     * Trigger kind identifiers for bitmask
     */
    public enum Kind {
        ASSEMBLED,
        EXTERNAL_FORCE,
        PLAYER_PROXIMITY,
        PROJECTILE,
        EXPLOSION,
        ;

        public int bit() {
            return 1 << ordinal();
        }

        public String displayName() {
            return switch (this) {
                case ASSEMBLED -> "Assembled";
                case EXTERNAL_FORCE -> "External Force";
                case PLAYER_PROXIMITY -> "Player Proximity";
                case PROJECTILE -> "Projectile";
                case EXPLOSION -> "Explosion";
            };
        }
    }
}
