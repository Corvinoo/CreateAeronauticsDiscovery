package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.PIPELINE;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class AssemblyStep {
    private final List<Op> ops = new ArrayList<>();
    private boolean built = false;
    private int cursor = 0;
    private int waitTicksRemaining = 0;
    private long maxTickOfExecution = -1;

    protected abstract void build(Sequence seq);

    protected int timeoutTicks() {
        return 200;
    }

    protected void onAbort(AssemblyContext ctx) {
    }

    public final AssemblyResult execute(AssemblyContext ctx) {
        if (maxTickOfExecution < 0) maxTickOfExecution = ctx.currentTick + timeoutTicks();

        if (ctx.currentTick > maxTickOfExecution) {
            ModLog.warn(PIPELINE,
                    "{}: Step '{}' timeout after {} tick(s), rollback..",
                    getClass().getSimpleName(), ctx.templateId, timeoutTicks());
            onAbort(ctx);
            return AssemblyResult.FAIL;
        }
        return tick(ctx);
    }

    private AssemblyResult tick(AssemblyContext ctx) {
        if (!built) {
            build(new Sequence(ops));
            built = true;
        }

        while (cursor < ops.size()) {
            if (waitTicksRemaining > 0) {
                waitTicksRemaining--;
                return AssemblyResult.WAITING;
            }

            OpResult result = ops.get(cursor).execute(ctx);
            switch (result) {
                case OpResult.Complete c -> {
                    return AssemblyResult.SUCCESS;
                }
                case OpResult.Continue c -> cursor++;
                case OpResult.Wait w -> {
                    return AssemblyResult.WAITING;
                }
                case OpResult.Delay d -> {
                    waitTicksRemaining = d.ticks();
                    cursor++;
                    return AssemblyResult.WAITING;
                }
                case OpResult.Skip s -> cursor += s.count();
                case OpResult.Fail f -> {
                    ModLog.warn(PIPELINE,
                            "Sequence failed at operation {}: {}",
                            getClass().getSimpleName(), cursor, f.reason());
                    return AssemblyResult.FAIL;
                }
            }
        }
        return AssemblyResult.SUCCESS;
    }

    public final void abort(AssemblyContext ctx) {
        onAbort(ctx);
        cursor = 0;
        waitTicksRemaining = 0;
        maxTickOfExecution = -1;
    }

    protected void forceEntityUpdate(AssemblyContext ctx) {
        AABB box = new AABB(ctx.templateBounds().minX(), ctx.templateBounds().minY(), ctx.templateBounds().minZ(),
                ctx.templateBounds().maxX(), ctx.templateBounds().maxY(), ctx.templateBounds().maxZ());
        ctx.level.getEntities((Entity) null, box, e -> true).forEach(Entity::tick);
    }

    @FunctionalInterface
    protected interface Op {
        OpResult execute(AssemblyContext ctx);
    }

    protected sealed interface OpResult {
        OpResult CONTINUE = new Continue();
        OpResult COMPLETE = new Complete();
        OpResult WAIT = new Wait();

        record Continue() implements OpResult {
        }

        record Wait() implements OpResult {
        }

        record Complete() implements OpResult {
        }

        record Delay(int ticks) implements OpResult {
        }

        record Skip(int count) implements OpResult {
        }

        record Fail(String reason) implements OpResult {
        }

        static OpResult delay(int ticks) {
            return new Delay(ticks);
        }

        static OpResult fail(String reason) {
            return new Fail(reason);
        }
    }

    protected static final class Sequence {
        private final List<Op> ops;

        private Sequence(List<Op> ops) {
            this.ops = ops;
        }

        public Sequence completeIf(Predicate<AssemblyContext> condition) {
            ops.add(ctx -> condition.test(ctx) ? OpResult.COMPLETE : OpResult.CONTINUE);
            return this;
        }

        public Sequence run(Consumer<AssemblyContext> action) {
            ops.add(ctx -> {
                action.accept(ctx);
                return OpResult.CONTINUE;
            });
            return this;
        }

        public Sequence delay(int ticks) {
            ops.add(ctx -> OpResult.delay(ticks));
            return this;
        }

        public Sequence waitUntil(Predicate<AssemblyContext> condition) {
            ops.add(ctx -> condition.test(ctx) ? OpResult.CONTINUE : OpResult.WAIT);
            return this;
        }

        public Sequence require(Predicate<AssemblyContext> condition, String failMessage) {
            ops.add(ctx -> condition.test(ctx) ? OpResult.CONTINUE : OpResult.fail(failMessage));
            return this;
        }

        public Sequence step(Function<AssemblyContext, AssemblyResult> subStep) {
            ops.add(ctx -> switch (subStep.apply(ctx)) {
                case SUCCESS -> OpResult.CONTINUE;
                case WAITING -> OpResult.WAIT;
                case FAIL -> OpResult.fail("sub-step FAIL");
            });
            return this;
        }

        public Sequence onlyIf(Predicate<AssemblyContext> condition, Consumer<Sequence> branch) {
            int markerIndex = ops.size();
            ops.add(ctx -> OpResult.CONTINUE);
            int before = ops.size();
            branch.accept(this);
            int branchLength = ops.size() - before;
            ops.set(markerIndex, ctx -> condition.test(ctx)
                    ? OpResult.CONTINUE
                    : new OpResult.Skip(branchLength));
            return this;
        }

        public Sequence retry(int maxAttempts, int delayTicks,
                              Function<AssemblyContext, Boolean> attempt, String giveUpMessage) {
            int[] attemptsLeft = {maxAttempts};
            ops.add(ctx -> {
                if (attempt.apply(ctx)) return OpResult.CONTINUE;
                attemptsLeft[0]--;
                if (attemptsLeft[0] <= 0) return OpResult.fail(giveUpMessage);
                return OpResult.delay(delayTicks);
            });
            return this;
        }
    }
}