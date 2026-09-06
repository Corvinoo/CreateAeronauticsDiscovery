package me.corvino.aeronauticsdiscovery.patrol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotPlan;
import me.corvino.aeronauticsdiscovery.event.BiomeFilter;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

/**
 * @param template        prefab template to assemble
 * @param targetStructure structure id (e.g. {@code minecraft:pillager_outpost}) or {@code #tag} to
 *                        patrol
 * @param chance          probability (0..1) that a given structure instance gets a patrol craft;
 *                        rolled once per instance and persisted so it never re-rolls
 * @param minAltitude     lower bound of the spawn altitude band
 * @param maxAltitude     upper bound of the spawn altitude band
 * @param initialVelocity optional velocity override applied on spawn (template-local frame, rotated
 *                        by the spawn yaw); if absent the template's {@code template_defaults}
 *                        velocity is used
 * @param plan            optional flight plan baked onto the craft at assembly; if absent the
 *                        template's {@code template_defaults} plan is used.
 * @param dimensions      dimensions the patrol is allowed to spawn in (empty = all)
 * @param biomeFilter     optional biome restriction, checked at the structure's position
 */
public record PatrolConfig(
        ResourceLocation template,
        String targetStructure,
        double chance,
        int minAltitude,
        int maxAltitude,
        Optional<InitialVelocity> initialVelocity,
        Optional<AutopilotPlan> plan,
        List<ResourceLocation> dimensions,
        BiomeFilter biomeFilter
) {
    public static final MapCodec<PatrolConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("template").forGetter(PatrolConfig::template),
            Codec.STRING.fieldOf("target_structure").forGetter(PatrolConfig::targetStructure),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("chance", 1.0).forGetter(PatrolConfig::chance),
            Codec.INT.optionalFieldOf("min_altitude", 150).forGetter(PatrolConfig::minAltitude),
            Codec.INT.optionalFieldOf("max_altitude", 200).forGetter(PatrolConfig::maxAltitude),
            InitialVelocity.CODEC.codec().optionalFieldOf("initial_velocity").forGetter(PatrolConfig::initialVelocity),
            AutopilotPlan.CODEC.codec().optionalFieldOf("plan").forGetter(PatrolConfig::plan),
            ResourceLocation.CODEC.listOf().optionalFieldOf("dimensions", List.of()).forGetter(PatrolConfig::dimensions),
            BiomeFilter.CODEC.optionalFieldOf("biome_filter", BiomeFilter.ALL).forGetter(PatrolConfig::biomeFilter)
    ).apply(instance, PatrolConfig::new));

    public boolean allowedIn(ResourceLocation dimension) {
        return dimensions.isEmpty() || dimensions.contains(dimension);
    }

    public boolean isEligible(ServerLevel level, BlockPos pos) {
        return allowedIn(level.dimension().location()) && biomeFilter().matches(level, pos);
    }
}
