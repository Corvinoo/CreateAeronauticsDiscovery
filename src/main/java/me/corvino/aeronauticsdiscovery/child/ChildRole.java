package me.corvino.aeronauticsdiscovery.child;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import net.minecraft.world.entity.Entity;

import java.util.function.Predicate;

public enum ChildRole {

    PERSISTENT {
        private static final double PROXIMITY_INFLATION = 10.0d;
        private static final Predicate<Entity> PRESERVE_PINS = entity -> entity instanceof PinEntity;

        @Override
        void handleCleanup(ChildContext ctx, ServerSubLevel child) {
            if (FlyoverUtils.isPlayerNearSubLevel(child, PROXIMITY_INFLATION)) {
                ctx.release(child, PRESERVE_PINS);
            } else {
                ctx.destroy(child);
            }
        }
    },

    TRANSIENT {
        @Override
        void handleCleanup(ChildContext ctx, ServerSubLevel child) {
            if (Config.fragmentPromotion && FlyoverUtils.isPlayerNearSubLevel(child, Config.promotionRange)) {
                ChildSubLevelManager.tagAs(child, PERSISTENT);
                ctx.release(child, entity -> entity instanceof PinEntity);
            } else {
                ctx.destroy(child);
            }
        }
    };

    abstract void handleCleanup(ChildContext ctx, ServerSubLevel child);

    private static final String PERSISTENT_KEY = "persistent";
    private static final String TRANSIENT_KEY = "transient";

    public String key() {
        return this == PERSISTENT ? PERSISTENT_KEY : TRANSIENT_KEY;
    }

    public static ChildRole fromKey(String key) {
        if (PERSISTENT_KEY.equals(key)) return PERSISTENT;
        if (TRANSIENT_KEY.equals(key)) return TRANSIENT;
        try {
            return valueOf(key);
        } catch (IllegalArgumentException e) {
            return TRANSIENT;
        }
    }
}
