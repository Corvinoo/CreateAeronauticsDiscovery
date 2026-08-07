package me.corvino.aeronauticsdiscovery.entities;

import me.corvino.aeronauticsdiscovery.autopilot.Autopilot;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotContext;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoal;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoalResolver;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotPlan;
import me.corvino.aeronauticsdiscovery.autopilot.RedstoneStabilizer;
import me.corvino.aeronauticsdiscovery.autopilot.goals.StraightFlightGoal;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.AUTOPILOT;

/**
 * A pillager that pilots an airborne craft exactly like a {@link SoaringTrader}, driven by the
 * shared {@link Autopilot} against whatever sub-level craft it is riding.
 */
public class PillagerAviator extends Pillager {
    private static final List<AutopilotGoal<?>> DEFAULT_GOALS = List.of(StraightFlightGoal.INSTANCE);

    private final Autopilot autopilot = new Autopilot();
    private final RedstoneStabilizer stabilizer = new RedstoneStabilizer(
            this, Items.GREEN_WOOL, Items.YELLOW_WOOL, Items.RED_WOOL, Items.LIGHT_BLUE_WOOL);
    @Nullable
    private AutopilotPlan lastPlan;

    public PillagerAviator(EntityType<? extends Pillager> type, Level level) {
        super(type, level);
        autopilot.configure(DEFAULT_GOALS);
    }

    private void tickAutopilot(ServerLevel serverLevel) {
        AutopilotContext context = AutopilotContext.of(this);
        if (context == null) {
            stabilizer.setAllInactive(serverLevel);
            lastPlan = null;
            return;
        }
        applyGoalsFor(context);
        stabilizer.tick(serverLevel, context, autopilot.bias(context));
    }

    private void applyGoalsFor(AutopilotContext context) {
        AutopilotPlan plan = AutopilotGoalResolver.plan(context);
        if (Objects.equals(plan, lastPlan)) return;
        lastPlan = plan;
        autopilot.configure(plan != null ? plan.goals() : DEFAULT_GOALS);
        ModLog.info(AUTOPILOT, "Aviator '{}' now flying {}",
                this.getName().getString(), plan != null ? plan.goals() : "DEFAULT (straight)");
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel) {
            tickAutopilot(serverLevel);
        }
    }
}