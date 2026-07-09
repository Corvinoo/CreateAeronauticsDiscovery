package me.corvino.aeronauticsdiscovery.pin.behaviour;

import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinNetwork;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;

/**
 * A single behaviour a {@link PinEntity} can carry. Implementations are decoded from the pin's
 * config tag via the {@link PinBehaviorType#codec()} they're registered under
 * <p>
 * Behaviours are intentionally NOT given direct references to other pins. Anything that needs to be
 * aware of other pins (chain-reaction propagation, network-wide state) goes through the static
 * {@link PinNetwork} utility (e.g. calling {@code PinNetwork.notifyTrigger(...)} from inside
 * {@link #onTrigger}), which resolves membership on demand rather than being walked by hand
 */
public interface PinBehavior<T extends PinBehavior<T>> {

    PinBehaviorType<T> type();

    /**
     * Called when a trigger reaches this pin — either because it was the origin of the trigger, or
     * because {@link PinNetwork} propagated one to it (e.g. a delayed chain-reaction). Call
     * {@code PinNetwork.notifyTrigger(...)} from here to keep the chain going.
     * <p>
     * The trigger {@link PinTrigger.Kind#ASSEMBLED} is dispatched once when the sub-level this
     * pin belongs to finishes assembly. Implementations that set up persistent sub-level features
     * (mob spawns, seat mounts, etc.) should respond to this kind.
     */
    default void onTrigger(PinEntity self, PinTrigger trigger) {
    }
}
