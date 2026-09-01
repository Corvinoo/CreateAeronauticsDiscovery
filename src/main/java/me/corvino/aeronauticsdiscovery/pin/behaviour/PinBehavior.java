package me.corvino.aeronauticsdiscovery.pin.behaviour;

import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinNetwork;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;

/**
 * A single behaviour a {@link PinEntity} can carry. Implementations are decoded from the pin's
 * config tag via the {@link PinBehaviorType#codec()} they're registered under
 */
public interface PinBehavior<T extends PinBehavior<T>> {

    PinBehaviorType<T> type();

    /**
     * Called when a trigger reaches this pin.
     */
    default void onTrigger(PinEntity self, PinTrigger trigger) {
    }
}
