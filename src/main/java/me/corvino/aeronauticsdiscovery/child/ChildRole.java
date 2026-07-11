package me.corvino.aeronauticsdiscovery.child;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import net.minecraft.world.entity.Entity;

import java.util.function.Predicate;

public enum ChildRole {

    PERSISTENT {
        private static final float PROXIMITY_INFLATION = 10.0f;
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

    FRAGMENT {
        @Override
        void handleCleanup(ChildContext ctx, ServerSubLevel child) {
            ctx.destroy(child);
        }
    };

    abstract void handleCleanup(ChildContext ctx, ServerSubLevel child);

    private static final String PERSISTENT_KEY = "persistent";
    private static final String FRAGMENT_KEY = "fragment";

    public String key() {
        return this == PERSISTENT ? PERSISTENT_KEY : FRAGMENT_KEY;
    }

    public static ChildRole fromKey(String key) {
        if (PERSISTENT_KEY.equals(key)) return PERSISTENT;
        if (FRAGMENT_KEY.equals(key)) return FRAGMENT;
        try {
            return valueOf(key);
        } catch (IllegalArgumentException e) {
            return FRAGMENT;
        }
    }
}
