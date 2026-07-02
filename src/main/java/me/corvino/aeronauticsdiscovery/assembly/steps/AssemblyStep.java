package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public abstract class AssemblyStep {
    //TODO: Implement a more declarative builder for step parts
    private final List<TickDelay> delays = new ArrayList<>();
    private final List<Guard> guards = new ArrayList<>();

    private long maxTickOfExecution = -1;

    /**
     * Process function that will be ticked until a SUCCESS o FAIL is returned.
     * @apiNote Returning {@link AssemblyResult#WAITING} will make the pipeline wait for this task to end.
     */
    protected abstract AssemblyResult tick(AssemblyContext ctx);

    /**
     * Timeout in ticks before the step is considered automatically failed.
     * @apiNote Default: 200 tick, and can be overridden.
     */
    protected int timeoutTicks() { return 200; }
    //TODO: probably this should be configurable per step? allowing for people to control this to manage unpredictable cases

    /**
     * This is called when step is interrupted before its completion
     * @apiNote This is useful to release stale resources
     */
    protected void onAbort(AssemblyContext ctx) {}


    public final AssemblyResult execute(AssemblyContext ctx) {
        if (maxTickOfExecution < 0) {
            maxTickOfExecution = ctx.currentTick + timeoutTicks();
        }

        if (ctx.currentTick > maxTickOfExecution) {
            CreateAeronauticsDiscovery.LOGGER.warn(
                    "[{}] Step using '{}' timeout after {} tick(s), attempting a rollback..",
                    getClass().getSimpleName(), ctx.templateId, timeoutTicks());
            onAbort(ctx);
            return AssemblyResult.FAIL;
        }

        return tick(ctx);
    }

    public final void abort(AssemblyContext ctx) {
        onAbort(ctx);
        delays.forEach(TickDelay::reset);
        guards.forEach(Guard::reset);
        maxTickOfExecution = -1;
    }

    /**
     * Creates a tick delayer and each one you make is independent of each other.
     * @return Ready to use tick delay, that MUST be started!
     */
    protected final TickDelay newDelay() {
        TickDelay delay = new TickDelay();
        delays.add(delay);
        return delay;
    }

    /**
     * Creates a single value flag and each one you make is independent of each other.
     * The {@link Guard} is very useful to have one-shots or for keeping tack of some simple boolean conditions.
     */
    protected final Guard newGuard() {
        var flag = new Guard();
        guards.add(flag);
        return flag;
    }

    /**
     * Forces entities to tick
     * @apiNote This is very useful, since placing entities in each step will not tick them right away,
     * and coupled with a {@link me.corvino.aeronauticsdiscovery.assembly.steps.AssemblyStep.TickDelay}
     * can let you control more easily entities lifecycles that won't update right when constructed.
     */
    protected void forceEntityUpdate(AssemblyContext ctx) {
        if (ctx.bounds == null || ctx.level == null) return;

        AABB searchBox = new AABB(
                ctx.bounds.minX(), ctx.bounds.minY(), ctx.bounds.minZ(),
                ctx.bounds.maxX(), ctx.bounds.maxY(), ctx.bounds.maxZ()
        );

        ctx.level.getEntities(
                (Entity) null,
                searchBox,
                entity -> true
        ).forEach(Entity::tick);
    }

    protected static final class TickDelay {
        /**
         * @apiNote Do not use this constructor, use the managed {@link AssemblyStep#newDelay()} instead.
         */
        private TickDelay() {};

        private int remaining = 0;

        public void start(int ticks) {
            if (remaining == 0) remaining = ticks;
        }


        public boolean isWaiting() {
            if (remaining <= 0) return false;
            remaining--;
            return remaining > 0;
        }

        public void reset() { remaining = 0; }
    }

    protected static final class Guard {
        /**
         * @apiNote Do not use this constructor, use the managed {@link AssemblyStep#newGuard()} instead.
         */
        private Guard() {};
        private boolean value = false;

        public boolean isSet() { return value; }
        public void set() { value = true; }
        public void reset() { value = false; }
    }
}