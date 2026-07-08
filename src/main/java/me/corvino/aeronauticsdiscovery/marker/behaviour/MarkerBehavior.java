package me.corvino.aeronauticsdiscovery.marker.behaviour;

import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import me.corvino.aeronauticsdiscovery.marker.MarkerNetwork;
import me.corvino.aeronauticsdiscovery.marker.MarkerTrigger;

/**
 * A single behaviour a {@link MarkerEntity} can carry. Implementations are decoded from the marker's
 * config tag via the {@link MarkerBehaviorType#codec()} they're registered under
 * <p>
 * Behaviours are intentionally NOT given direct references to other markers. Anything that needs to be
 * aware of other markers (chain-reaction propagation, network-wide state) goes through the static
 * {@link MarkerNetwork} utility (e.g. calling {@code MarkerNetwork.notifyTrigger(...)} from inside
 * {@link #onTrigger}), which resolves membership on demand rather than being walked by hand
 */
public interface MarkerBehavior<T extends MarkerBehavior<T>> {

    MarkerBehaviorType<T> type();

    /**
     * Called when a trigger reaches this marker — either because it was the origin of the trigger, or
     * because {@link MarkerNetwork} propagated one to it (e.g. a delayed chain-reaction). Call
     * {@code MarkerNetwork.notifyTrigger(...)} from here to keep the chain going.
     * <p>
     * The trigger {@link MarkerTrigger.Kind#ASSEMBLED} is dispatched once when the sub-level this
     * marker belongs to finishes assembly. Implementations that set up persistent sub-level features
     * (mob spawns, seat mounts, etc.) should respond to this kind.
     */
    default void onTrigger(MarkerEntity self, MarkerTrigger trigger) {
    }
}
