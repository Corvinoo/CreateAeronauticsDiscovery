package me.corvino.aeronauticsdiscovery.autopilot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A flight plan: the ordered set of {@link AutopilotGoal}s a pilot mob flies with. Goals are decoded
 * by their registered {@link AutopilotGoalType#id()} via the {@code "type"} field.
 * <p>
 * A plan is fully self-contained serializable data. It travels to a mob inside the craft's user-data
 * tag (written by the assembly finalizer) 
 *
 * @param goals the goals to configure, in no particular order
 */
public record AutopilotPlan(List<AutopilotGoal<?>> goals) {

    public static final Codec<AutopilotGoal<?>> GOAL_CODEC = Codec.STRING.dispatch(
            "type",
            goal -> goal.type().id().toString(),
            id -> AutopilotGoalTypes.codecFor(ResourceLocation.parse(id)));

    public static final MapCodec<AutopilotPlan> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            GOAL_CODEC.listOf().fieldOf("goals").forGetter(AutopilotPlan::goals)
    ).apply(instance, AutopilotPlan::new));
}
