package me.corvino.aeronauticsdiscovery.autopilot;

import me.corvino.aeronauticsdiscovery.util.ModLog;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.GENERAL;

public final class Autopilot {

    private final Map<GoalCategory, AutopilotGoal> goals = new EnumMap<>(GoalCategory.class);

    /**
     * Register a goal. Fails (returns {@code false}) when a goal of the same
     * {@link GoalCategory category} is already registered; contradictory goals must never coexist.
     */
    public boolean addGoal(AutopilotGoal goal) {
        GoalCategory category = goal.category();
        AutopilotGoal existing = goals.get(category);
        if (existing != null) {
            ModLog.warn(GENERAL, "Autopilot: goal {} conflicts with existing {} in category {}, rejected",
                    goal.name(), existing.name(), category);
            return false;
        }
        goals.put(category, goal);
        return true;
    }

    public boolean removeGoal(GoalCategory category) {
        return goals.remove(category) != null;
    }

    @Nullable
    public AutopilotGoal getGoal(GoalCategory category) {
        return goals.get(category);
    }

    public Set<GoalCategory> activeCategories() {
        return goals.keySet();
    }

    /** Combine every active goal's bias into a single steering command for this tick. */
    public AutopilotBias bias(AutopilotContext context) {
        AutopilotBias total = AutopilotBias.NONE;
        for (AutopilotGoal goal : goals.values()) {
            AutopilotBias bias = goal.bias(context);
            if (bias != null) total = total.add(bias);
        }
        return total;
    }
}
