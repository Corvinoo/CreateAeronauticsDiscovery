package me.corvino.aeronauticsdiscovery.autopilot;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Identifies an {@link AutopilotGoal} implementation and how to (de)serialize it from a datapack
 * goal set. Adding a new goal means implementing {@link AutopilotGoal} + a {@link MapCodec} for its
 * parameters and registering one of these in {@link AutopilotGoalTypes}.
 */
public record AutopilotGoalType<T extends AutopilotGoal<T>>(ResourceLocation id, MapCodec<T> codec) {
}
